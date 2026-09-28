package com.domen.apollowaypoints.client.gui;

import com.domen.apollowaypoints.client.ApolloWaypointsClient;
import com.domen.apollowaypoints.client.ClientSettings;
import com.domen.apollowaypoints.client.ClientState;
import com.domen.apollowaypoints.client.Requests;
import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Dimensions;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.net.PermissionFlags;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The mod's main screen (key J or /wpgui): every server waypoint, with add, edit, remove, teleport and hide. */
public class WaypointsScreen extends Screen {
	private static final String EXPORT_SET = "Apollo";

	private enum Where {
		ALL("все"), HERE("текущее"), OVERWORLD("верхний мир"), NETHER("незер"), END("энд");

		final String label;

		Where(String label) {
			this.label = label;
		}

		boolean matches(String dimension, String here) {
			return switch (this) {
				case ALL -> true;
				case HERE -> dimension.equals(here);
				case OVERWORLD -> dimension.equals(Dimensions.OVERWORLD);
				case NETHER -> dimension.equals(Dimensions.NETHER);
				case END -> dimension.equals(Dimensions.END);
			};
		}
	}

	// Filters survive closing and reopening the screen.
	private static String search = "";
	private static Where where = Where.ALL;
	private static String categoryFilter = "";

	private WaypointList list;
	private Button addButton;
	private Button editButton;
	private Button removeButton;
	private Button teleportButton;
	private Button hideButton;
	private Button importButton;
	private Button exportButton;
	private int listTop;
	private int seenVersion = -1;
	private boolean confirmRemove;
	private boolean confirmExport;
	private Component status = Component.empty();
	private int statusColor = Ui.GRAY;

	public WaypointsScreen() {
		super(Component.literal("Точки сервера"));
	}

	@Override
	protected void init() {
		int margin = 10;
		int top = 22;
		int filterWidth = Math.min(140, (width - 2 * margin - 8) / 4);

		EditBox searchBox = new EditBox(font, margin, top, width - 2 * margin - 2 * filterWidth - 8, 20, Component.literal("Поиск"));
		searchBox.setHint(Component.literal("Поиск: имя, описание, категория"));
		searchBox.setValue(search);
		searchBox.setResponder(value -> {
			search = value;
			rebuild();
		});
		addRenderableWidget(searchBox);

		addRenderableWidget(CycleButton.builder((Where w) -> Component.literal(w.label), where).withValues(Where.values())
			.create(width - margin - 2 * filterWidth - 4, top, filterWidth, 20, Component.literal("Где"), (button, value) -> {
				where = value;
				rebuild();
			}));

		List<String> categories = new ArrayList<>();
		categories.add("");
		categories.addAll(ClientState.categoryIds());
		if (!categories.contains(categoryFilter)) {
			categoryFilter = "";
		}
		addRenderableWidget(CycleButton.builder(WaypointsScreen::categoryLabel, categoryFilter).withValues(categories)
			.create(width - margin - filterWidth, top, filterWidth, 20, Component.literal("Категория"), (button, value) -> {
				categoryFilter = value;
				rebuild();
			}));

		listTop = top + 26;
		list = new WaypointList(minecraft, width, height - 66 - listTop, listTop, this::openEditor, this::onSelectionChanged);
		addRenderableWidget(list);

		int rowWidth = Math.min(width - 2 * margin, 520);
		int left = (width - rowWidth) / 2;
		int gap = 4;
		int fifth = (rowWidth - 4 * gap) / 5;
		int row1 = height - 50;
		addButton = button("Добавить", left, row1, fifth, b -> minecraft.gui.setScreen(new EditScreen(this, null)));
		editButton = button("Изменить", left + (fifth + gap), row1, fifth, b -> openEditor(list.selected()));
		removeButton = button("Удалить", left + 2 * (fifth + gap), row1, fifth, b -> remove());
		teleportButton = button("Телепорт", left + 3 * (fifth + gap), row1, fifth, b -> teleport());
		hideButton = button("Скрыть", left + 4 * (fifth + gap), row1, fifth, b -> toggleHidden());
		hideButton.setTooltip(Tooltip.create(Component.literal("Скрыть точку на карте только для себя")));

		int quarter = (rowWidth - 3 * gap) / 4;
		int row2 = height - 26;
		importButton = button("Из Xaero…", left, row2, quarter, b -> minecraft.gui.setScreen(new XaeroImportScreen(this)));
		importButton.setTooltip(Tooltip.create(Component.literal("Отправить на сервер точки из своих наборов Xaero")));
		exportButton = button("В набор Xaero", left + (quarter + gap), row2, quarter, b -> exportToSet());
		exportButton.setTooltip(Tooltip.create(Component.literal("Скопировать серверные точки в обычный набор Xaero «" + EXPORT_SET
			+ "» — останутся у тебя и без сервера. Содержимое этого набора заменяется.")));
		button(toastsLabel(), left + 2 * (quarter + gap), row2, quarter, b -> {
			ClientSettings.setToasts(!ClientSettings.toasts());
			b.setMessage(Component.literal(toastsLabel()));
		}).setTooltip(Tooltip.create(Component.literal("Всплывающее уведомление, когда кто-то добавляет точку")));
		button("Готово", left + 3 * (quarter + gap), row2, quarter, b -> onClose());

		rebuild();
	}

	private Button button(String label, int x, int y, int width, Button.OnPress onPress) {
		return addRenderableWidget(Button.builder(Component.literal(label), onPress).bounds(x, y, width, 20).build());
	}

	@Override
	public void tick() {
		if (seenVersion != ClientState.version()) {
			rebuild();
		} else {
			updateButtons();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.centeredText(font, Component.literal("Точки сервера · " + ClientState.waypoints().size()), width / 2, 8, Ui.WHITE);
		if (list.isEmpty()) {
			g.centeredText(font, ClientState.waypoints().isEmpty() ? "Точек пока нет — нажми «Добавить»" : "Ничего не найдено", width / 2, listTop + 20, Ui.GRAY);
		}
		if (!ApolloWaypointsClient.xaero().available()) {
			g.centeredText(font, "Xaero's Minimap не найден: точки видны только в этом списке", width / 2, height - 62, Ui.YELLOW);
		} else {
			g.centeredText(font, status, width / 2, height - 62, statusColor);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	void setStatus(String text, boolean ok) {
		status = Component.literal(text);
		statusColor = ok ? Ui.GREEN : Ui.RED;
	}

	private void rebuild() {
		seenVersion = ClientState.version();
		String here = minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
		Vec3 position = minecraft.player == null ? null : minecraft.player.position();
		String query = search.trim().toLowerCase(Locale.ROOT);
		List<Waypoint> shown = ClientState.waypoints().stream()
			.filter(w -> where.matches(w.dimension(), here))
			.filter(w -> categoryFilter.isEmpty() || w.category().equals(categoryFilter))
			.filter(w -> query.isEmpty() || matches(w, query))
			.sorted(Comparator.comparingInt((Waypoint w) -> w.dimension().equals(here) ? 0 : 1)
				.thenComparingDouble(w -> w.dimension().equals(here) && position != null ? w.distanceSq(position.x, position.y, position.z) : 0)
				.thenComparing(w -> w.name().toLowerCase(Locale.ROOT)))
			.toList();
		list.setWaypoints(shown, here, position);
		updateButtons();
	}

	private static boolean matches(Waypoint w, String query) {
		Category category = ClientState.category(w.category());
		return w.name().toLowerCase(Locale.ROOT).contains(query)
			|| w.description().toLowerCase(Locale.ROOT).contains(query)
			|| (category != null && category.name().toLowerCase(Locale.ROOT).contains(query))
			|| ("#" + w.id()).equals(query);
	}

	private void updateButtons() {
		Waypoint w = list.selected();
		boolean xaero = ApolloWaypointsClient.xaero().available();
		addButton.active = ClientState.can(PermissionFlags.ADD);
		editButton.active = w != null && ClientState.canEdit(w);
		removeButton.active = w != null && ClientState.canRemove(w);
		teleportButton.active = w != null && ClientState.canTeleportNow();
		hideButton.active = w != null && xaero;
		hideButton.setMessage(Component.literal(w != null && ClientSettings.isHidden(w.id()) ? "Показать" : "Скрыть"));
		importButton.active = xaero && ClientState.can(PermissionFlags.ADD);
		exportButton.active = xaero && !ClientState.waypoints().isEmpty();
		if (!confirmRemove) {
			removeButton.setMessage(Component.literal("Удалить"));
		}
		if (!confirmExport) {
			exportButton.setMessage(Component.literal("В набор Xaero"));
		}
	}

	private void onSelectionChanged() {
		confirmRemove = false;
		updateButtons();
	}

	private void openEditor(Waypoint w) {
		if (w != null && ClientState.canEdit(w)) {
			minecraft.gui.setScreen(new EditScreen(this, w));
		}
	}

	private void remove() {
		Waypoint w = list.selected();
		if (w == null) {
			return;
		}
		if (!confirmRemove) {
			confirmRemove = true;
			removeButton.setMessage(Component.literal("Точно?").withColor(Ui.RED));
			return;
		}
		confirmRemove = false;
		Requests.remove(w.id(), response -> setStatus(response.message()
			+ (response.ok() ? ". Вернуть: /wp restore " + w.id() : ""), response.ok()));
	}

	private void teleport() {
		Waypoint w = list.selected();
		if (w == null) {
			return;
		}
		Requests.teleport(w.id(), response -> {
			if (response.ok()) {
				onClose();
			} else {
				setStatus(response.message(), false);
			}
		});
	}

	private void toggleHidden() {
		Waypoint w = list.selected();
		if (w != null) {
			ApolloWaypointsClient.xaero().setHidden(w.id(), !ClientSettings.isHidden(w.id()));
			rebuild();
		}
	}

	private void exportToSet() {
		if (!confirmExport) {
			confirmExport = true;
			exportButton.setMessage(Component.literal("Заменить набор?").withColor(Ui.YELLOW));
			return;
		}
		confirmExport = false;
		setStatus(ApolloWaypointsClient.xaero().exportToSet(EXPORT_SET), true);
	}

	private static Component categoryLabel(String id) {
		if (id.isEmpty()) {
			return Component.literal("все");
		}
		Category category = ClientState.category(id);
		return Component.literal(category == null ? id : category.name());
	}

	private static String toastsLabel() {
		return "Уведомления: " + (ClientSettings.toasts() ? "вкл" : "выкл");
	}
}
