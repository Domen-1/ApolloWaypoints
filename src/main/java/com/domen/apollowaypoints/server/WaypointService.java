package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Colors;
import com.domen.apollowaypoints.model.Dimensions;
import com.domen.apollowaypoints.model.Validation;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.model.WaypointDraft;
import com.domen.apollowaypoints.net.Payloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Every change to waypoints goes through here, whether it comes from a command, the client screen, a Xaero share
 * or an import: permission check, validation, storage, history and broadcast to synced clients.
 */
public final class WaypointService {
	private static final int MAX_CATEGORY_NAME = 24;

	private final MinecraftServer server;
	private final WaypointStore store;
	private final HistoryLog history;
	private final Syncer syncer;

	WaypointService(MinecraftServer server, WaypointStore store, HistoryLog history, Syncer syncer) {
		this.server = server;
		this.store = store;
		this.history = history;
		this.syncer = syncer;
	}

	public WaypointStore store() {
		return store;
	}

	public HistoryLog history() {
		return history;
	}

	// ---- queries

	public Waypoint findByName(String dimension, String name) {
		for (Waypoint w : store.all()) {
			if (w.dimension().equals(dimension) && w.name().equalsIgnoreCase(name)) {
				return w;
			}
		}
		return null;
	}

	public List<Waypoint> findByName(String name) {
		List<Waypoint> found = new ArrayList<>();
		for (Waypoint w : store.all()) {
			if (w.name().equalsIgnoreCase(name)) {
				found.add(w);
			}
		}
		return found;
	}

	public ResourceKey<Level> dimensionKey(String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		if (id == null) {
			return null;
		}
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
		return server.levelKeys().contains(key) ? key : null;
	}

	public boolean isKnownDimension(String dimension) {
		return dimensionKey(dimension) != null;
	}

	/** "Name", or "Name 2", "Name 3"... if the dimension already has a waypoint with that name. */
	public String uniqueName(String dimension, String name) {
		String candidate = name;
		for (int i = 2; findByName(dimension, candidate) != null; i++) {
			String suffix = " " + i;
			candidate = name.substring(0, Math.min(name.length(), Validation.MAX_NAME - suffix.length())) + suffix;
		}
		return candidate;
	}

	public boolean canEdit(Actor actor, Waypoint w) {
		return Perms.check(actor.permissions(), w.owner().equals(actor.uuid()) ? Perms.Perm.EDIT_OWN : Perms.Perm.EDIT_ANY);
	}

	public boolean canRemove(Actor actor, Waypoint w) {
		return Perms.check(actor.permissions(), w.owner().equals(actor.uuid()) ? Perms.Perm.REMOVE_OWN : Perms.Perm.REMOVE_ANY);
	}

	public boolean canTeleport(Actor actor) {
		if (Perms.check(actor.permissions(), Perms.Perm.TP)) {
			return true;
		}
		return WaypointServer.config().tpInSpectator && actor.player() != null && actor.player().isSpectator();
	}

	// ---- waypoint changes

	public Result create(Actor actor, WaypointDraft draft) {
		return create(actor, draft, Payloads.Change.CREATED);
	}

	private Result create(Actor actor, WaypointDraft draft, Payloads.Change change) {
		if (store.isReadOnly()) {
			return readOnly();
		}
		if (!Perms.check(actor.permissions(), Perms.Perm.ADD)) {
			return Result.error("Нет прав добавлять точки");
		}
		int limit = WaypointServer.config().maxWaypointsPerPlayer;
		if (limit > 0 && !actor.uuid().equals(Waypoint.CONSOLE)
			&& store.all().stream().filter(w -> w.owner().equals(actor.uuid())).count() >= limit) {
			return Result.error("У тебя уже " + limit + " точек — это предел на игрока");
		}
		WaypointDraft d = draft.copy();
		String error = normalize(d, -1);
		if (error != null) {
			return Result.error(error);
		}
		long now = System.currentTimeMillis();
		Waypoint w = build(d, store.allocateId(), actor.uuid(), actor.name(), now, actor.name(), now);
		store.put(w);
		history.add(actor.name(), change == Payloads.Change.IMPORTED ? "imported" : "created", w.id(), w.name(),
			w.coordinates() + " " + Dimensions.displayName(w.dimension()));
		syncer.broadcast(new Payloads.Upserted(w, change, actor.name()));
		if (change == Payloads.Change.CREATED && WaypointServer.config().announceInChat) {
			server.getPlayerList().broadcastSystemMessage(ChatUi.announcement(actor.name(), w), false);
		}
		return Result.ok("Добавлена точка «" + w.name() + "» (#" + w.id() + ")", w);
	}

	public Result update(Actor actor, int id, WaypointDraft draft) {
		if (store.isReadOnly()) {
			return readOnly();
		}
		Waypoint old = store.get(id);
		if (old == null) {
			return Result.error("Точки #" + id + " нет");
		}
		if (!canEdit(actor, old)) {
			return Result.error("Нет прав менять чужие точки");
		}
		WaypointDraft d = draft.copy();
		followCategory(old, d);
		String error = normalize(d, id);
		if (error != null) {
			return Result.error(error);
		}
		Waypoint w = build(d, id, old.owner(), old.ownerName(), old.createdAt(), actor.name(), System.currentTimeMillis());
		String diff = diff(old, w);
		if (diff.isEmpty()) {
			return Result.ok("Точка «" + old.name() + "» не изменилась", old);
		}
		store.put(w);
		history.add(actor.name(), "updated", id, w.name(), diff);
		syncer.broadcast(new Payloads.Upserted(w, Payloads.Change.UPDATED, actor.name()));
		return Result.ok("Точка «" + w.name() + "» (#" + id + ") изменена: " + diff, w);
	}

	public Result remove(Actor actor, int id) {
		if (store.isReadOnly()) {
			return readOnly();
		}
		Waypoint w = store.get(id);
		if (w == null) {
			return Result.error("Точки #" + id + " нет");
		}
		if (!canRemove(actor, w)) {
			return Result.error("Нет прав удалять чужие точки");
		}
		store.moveToTrash(w, actor.name());
		history.add(actor.name(), "removed", id, w.name(), "");
		syncer.broadcast(new Payloads.Removed(id, actor.name()));
		return Result.ok("Точка «" + w.name() + "» (#" + id + ") удалена", w);
	}

	public Result restore(Actor actor, int id) {
		if (store.isReadOnly()) {
			return readOnly();
		}
		WaypointStore.Trashed trashed = store.getTrashed(id);
		if (trashed == null) {
			return Result.error("В корзине нет точки #" + id);
		}
		Waypoint w = trashed.waypoint();
		if (!canRemove(actor, w)) {
			return Result.error("Нет прав возвращать чужие точки");
		}
		Waypoint clash = findByName(w.dimension(), w.name());
		if (clash != null) {
			return Result.error("В этом измерении уже есть точка «" + clash.name() + "» (#" + clash.id() + "), сначала переименуй её");
		}
		if (store.category(w.category()) == null) {
			w = withCategory(w, defaultCategory().id());
		}
		store.takeFromTrash(id);
		store.put(w);
		history.add(actor.name(), "restored", id, w.name(), "");
		syncer.broadcast(new Payloads.Upserted(w, Payloads.Change.RESTORED, actor.name()));
		return Result.ok("Точка «" + w.name() + "» (#" + id + ") возвращена", w);
	}

	public Result teleport(Actor actor, int id) {
		ServerPlayer player = actor.player();
		if (player == null) {
			return Result.error("Телепорт работает только для игрока");
		}
		Waypoint w = store.get(id);
		if (w == null) {
			return Result.error("Точки #" + id + " нет");
		}
		if (!canTeleport(actor)) {
			return Result.error("Телепорт к точкам — только для опов или в режиме наблюдателя");
		}
		ResourceKey<Level> key = dimensionKey(w.dimension());
		ServerLevel level = key == null ? null : server.getLevel(key);
		if (level == null) {
			return Result.error("Измерения " + w.dimension() + " на сервере нет");
		}
		double y;
		if (w.y() != null) {
			y = w.y();
		} else if (level.dimensionType().hasCeiling()) {
			// The heightmap would put the player on the Nether roof: look for a free spot under it instead.
			y = safeYUnderCeiling(level, w.x(), w.z(), player.level() == level ? (int) player.getY() : 64);
		} else {
			y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, w.x(), w.z());
		}
		float yRot = w.yaw() != null ? w.yaw() : player.getYRot();
		float xRot = w.yaw() != null ? 0 : player.getXRot();
		player.teleportTo(level, w.x() + 0.5, y, w.z() + 0.5, Set.of(), yRot, xRot, true);
		return Result.ok("Телепорт к «" + w.name() + "»", w);
	}

	/** Highest y below the roof with air for feet and head and a solid block underneath; {@code fallback} if none. */
	private static int safeYUnderCeiling(ServerLevel level, int x, int z, int fallback) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int top = level.getMinY() + level.dimensionType().logicalHeight() - 2;
		for (int y = top; y > level.getMinY(); y--) {
			pos.set(x, y, z);
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
				return y;
			}
		}
		return fallback;
	}

	public record ImportSummary(int added, int skipped, List<String> errors) {
	}

	/** Adds the drafts that do not clash by name; used by /wp import and the client's "send from Xaero". */
	public ImportSummary importDrafts(Actor actor, List<WaypointDraft> drafts) {
		int added = 0;
		int skipped = 0;
		List<String> errors = new ArrayList<>();
		for (WaypointDraft draft : drafts) {
			String name = draft.name == null ? "" : draft.name.trim();
			if (findByName(draft.dimension, name) != null) {
				skipped++;
				continue;
			}
			Result result = create(actor, draft, Payloads.Change.IMPORTED);
			if (result.ok()) {
				added++;
			} else {
				errors.add("«" + name + "»: " + result.message());
			}
		}
		return new ImportSummary(added, skipped, errors);
	}

	// ---- categories

	public Category defaultCategory() {
		Category c = store.category(WaypointServer.config().defaultCategory);
		return c != null ? c : store.categories().getFirst();
	}

	public Result addCategory(Actor actor, String id, String name) {
		if (store.isReadOnly()) {
			return readOnly();
		}
		if (!Perms.check(actor.permissions(), Perms.Perm.ADMIN)) {
			return Result.error("Категориями управляют админы");
		}
		if (!Category.isValidId(id)) {
			return Result.error("Id категории — латиница, цифры и _, до 24 символов");
		}
		if (store.category(id) != null) {
			return Result.error("Категория " + id + " уже есть");
		}
		String error = checkCategoryName(name);
		if (error != null) {
			return Result.error(error);
		}
		store.putCategory(new Category(id, name.trim(), 15, WaypointServer.config().defaultVisibility));
		history.add(actor.name(), "category_added", 0, id, name.trim());
		broadcastCategories();
		return Result.ok("Категория «" + name.trim() + "» (" + id + ") добавлена", null);
	}

	public Result updateCategory(Actor actor, Category category) {
		if (store.isReadOnly()) {
			return readOnly();
		}
		if (!Perms.check(actor.permissions(), Perms.Perm.ADMIN)) {
			return Result.error("Категориями управляют админы");
		}
		if (store.category(category.id()) == null) {
			return Result.error("Нет категории " + category.id());
		}
		String error = checkCategoryName(category.name());
		if (error != null) {
			return Result.error(error);
		}
		store.putCategory(category);
		history.add(actor.name(), "category_updated", 0, category.id(), category.name());
		broadcastCategories();
		return Result.ok("Категория «" + category.name() + "» обновлена", null);
	}

	/** Moves the category's waypoints to the default category first. */
	public Result removeCategory(Actor actor, String id) {
		if (store.isReadOnly()) {
			return readOnly();
		}
		if (!Perms.check(actor.permissions(), Perms.Perm.ADMIN)) {
			return Result.error("Категориями управляют админы");
		}
		Category category = store.category(id);
		if (category == null) {
			return Result.error("Нет категории " + id);
		}
		if (store.categories().size() == 1) {
			return Result.error("Нельзя удалить последнюю категорию");
		}
		Category target = defaultCategory();
		if (target.id().equals(id)) {
			target = store.categories().stream().filter(c -> !c.id().equals(id)).findFirst().orElseThrow();
		}
		int moved = 0;
		for (Waypoint w : List.copyOf(store.all())) {
			if (w.category().equals(id)) {
				Waypoint updated = withCategory(w, target.id());
				store.put(updated);
				syncer.broadcast(new Payloads.Upserted(updated, Payloads.Change.UPDATED, actor.name()));
				moved++;
			}
		}
		store.removeCategory(id);
		history.add(actor.name(), "category_removed", 0, id, "точки перенесены в " + target.id());
		broadcastCategories();
		return Result.ok("Категория «" + category.name() + "» удалена" + (moved > 0 ? ", " + moved + " точек перенесено в «" + target.name() + "»" : ""), null);
	}

	private void broadcastCategories() {
		syncer.broadcast(new Payloads.Categories(new ArrayList<>(store.categories())));
	}

	// ---- helpers

	/** Canonicalizes the draft in place; returns an error message or null. */
	private String normalize(WaypointDraft d, int selfId) {
		d.name = d.name == null ? "" : d.name.trim();
		String error = Validation.checkName(d.name);
		if (error != null) {
			return error;
		}
		d.symbol = d.symbol == null ? "" : d.symbol.trim();
		if (d.symbol.isEmpty()) {
			d.symbol = Validation.defaultSymbol(d.name);
		}
		error = Validation.checkSymbol(d.symbol);
		if (error != null) {
			return error;
		}
		if (d.dimension == null || !isKnownDimension(d.dimension)) {
			return "На сервере нет измерения " + d.dimension;
		}
		error = Validation.checkPosition(d.x, d.y, d.z);
		if (error != null) {
			return error;
		}
		if (d.yaw != null) {
			d.yaw = Mth.wrapDegrees(d.yaw);
		}
		if (d.category == null || d.category.isBlank()) {
			d.category = defaultCategory().id();
		}
		Category category = store.category(d.category);
		if (category == null) {
			return "Нет категории «" + d.category + "»";
		}
		if (d.color < 0) {
			d.color = category.color();
		} else if (!Colors.isValid(d.color)) {
			return "Неизвестный цвет " + d.color;
		}
		if (d.visibility == null) {
			d.visibility = category.visibility();
		}
		d.description = d.description == null ? "" : d.description.trim();
		error = Validation.checkDescription(d.description);
		if (error != null) {
			return error;
		}
		Waypoint clash = findByName(d.dimension, d.name);
		if (clash != null && clash.id() != selfId) {
			return "В измерении «" + Dimensions.displayName(d.dimension) + "» уже есть точка «" + clash.name() + "» (#" + clash.id() + ")";
		}
		return null;
	}

	private static Waypoint build(WaypointDraft d, int id, java.util.UUID owner, String ownerName, long createdAt, String updatedBy, long updatedAt) {
		return new Waypoint(id, d.name, d.symbol, d.dimension, d.x, d.y, d.z, d.yaw, d.color, d.category, d.visibility,
			d.description, owner, ownerName, createdAt, updatedBy, updatedAt);
	}

	/**
	 * When a waypoint moves to another category, its color and visibility follow the new category
	 * unless they were set by hand (i.e. differ from the old category's).
	 */
	private void followCategory(Waypoint old, WaypointDraft d) {
		if (d.category == null || d.category.equals(old.category())) {
			return;
		}
		Category from = store.category(old.category());
		Category to = store.category(d.category);
		if (to == null) {
			return;
		}
		if (d.color == old.color() && (from == null || from.color() == old.color())) {
			d.color = to.color();
		}
		if (d.visibility == old.visibility() && (from == null || from.visibility() == old.visibility())) {
			d.visibility = to.visibility();
		}
	}

	private Waypoint withCategory(Waypoint w, String category) {
		WaypointDraft d = w.draft();
		d.category = category;
		followCategory(w, d);
		return build(d, w.id(), w.owner(), w.ownerName(), w.createdAt(), w.updatedBy(), w.updatedAt());
	}

	private String diff(Waypoint a, Waypoint b) {
		List<String> changes = new ArrayList<>();
		if (!a.name().equals(b.name())) {
			changes.add("имя «" + a.name() + "» → «" + b.name() + "»");
		}
		if (a.x() != b.x() || !Objects.equals(a.y(), b.y()) || a.z() != b.z()) {
			changes.add("координаты " + a.coordinates() + " → " + b.coordinates());
		}
		if (!a.dimension().equals(b.dimension())) {
			changes.add("измерение " + Dimensions.displayName(a.dimension()) + " → " + Dimensions.displayName(b.dimension()));
		}
		if (a.color() != b.color()) {
			changes.add("цвет " + Colors.name(a.color()) + " → " + Colors.name(b.color()));
		}
		if (!a.category().equals(b.category())) {
			changes.add("категория " + categoryName(a.category()) + " → " + categoryName(b.category()));
		}
		if (!a.symbol().equals(b.symbol())) {
			changes.add("символ " + a.symbol() + " → " + b.symbol());
		}
		if (a.visibility() != b.visibility()) {
			changes.add("видимость: " + b.visibility().description);
		}
		if (!Objects.equals(a.yaw(), b.yaw())) {
			changes.add(b.yaw() == null ? "поворот убран" : "поворот " + b.yaw() + "°");
		}
		if (!a.description().equals(b.description())) {
			changes.add(b.description().isEmpty() ? "описание убрано" : "описание");
		}
		return String.join(", ", changes);
	}

	private String categoryName(String id) {
		Category c = store.category(id);
		return c == null ? id : c.name().toLowerCase(Locale.ROOT);
	}

	private static String checkCategoryName(String name) {
		if (name == null || name.isBlank()) {
			return "Название категории не может быть пустым";
		}
		if (name.trim().length() > MAX_CATEGORY_NAME) {
			return "Название категории — не больше " + MAX_CATEGORY_NAME + " символов";
		}
		return name.indexOf('§') >= 0 ? "В названии есть недопустимые символы" : null;
	}

	private Result readOnly() {
		return Result.error("Файл точек не прочитался (" + store.loadError() + "), изменения отключены. Смотри лог сервера.");
	}
}
