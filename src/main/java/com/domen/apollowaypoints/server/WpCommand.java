package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.format.XaeroFile;
import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Colors;
import com.domen.apollowaypoints.model.Dimensions;
import com.domen.apollowaypoints.model.Visibility;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.model.WaypointDraft;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * /wp. A waypoint is referred to by name or number ("12" or "#12"). Names come last where possible, so Russian names
 * with spaces work without quotes; where more arguments follow, use the number or put the name in quotes.
 */
public final class WpCommand {
	private static final int PAGE_SIZE = 10;
	private static final int NEAR_LIMIT = 10;
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static final String EXPORT_SET = "Apollo";

	private static final SuggestionProvider<CommandSourceStack> REFS = (ctx, builder) -> SharedSuggestionProvider.suggest(
		Stream.concat(live().stream().map(Waypoint::name), live().stream().map(w -> "#" + w.id())), builder);
	private static final SuggestionProvider<CommandSourceStack> QUOTED_REFS = (ctx, builder) -> SharedSuggestionProvider.suggest(
		Stream.concat(live().stream().map(w -> String.valueOf(w.id())), live().stream().map(w -> StringArgumentType.escapeIfRequired(w.name()))), builder);
	private static final SuggestionProvider<CommandSourceStack> TRASH_REFS = (ctx, builder) -> SharedSuggestionProvider.suggest(
		service().store().trash().stream().map(t -> "#" + t.waypoint().id()), builder);
	private static final SuggestionProvider<CommandSourceStack> CATEGORIES = (ctx, builder) -> SharedSuggestionProvider.suggest(
		service().store().categories().stream().map(Category::id), builder);
	private static final SuggestionProvider<CommandSourceStack> COLORS = (ctx, builder) -> SharedSuggestionProvider.suggest(Colors.ids(), builder);
	/** Suggests "file" and "category file" for /wp import. */
	private static final SuggestionProvider<CommandSourceStack> IMPORT_FILES = (ctx, builder) -> {
		List<String> files;
		try (Stream<Path> list = Files.list(importDir())) {
			files = list.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).toList();
		} catch (IOException e) {
			return builder.buildFuture();
		}
		List<String> options = new ArrayList<>(files);
		for (Category c : service().store().categories()) {
			files.forEach(f -> options.add(c.id() + " " + f));
		}
		return SharedSuggestionProvider.suggest(options, builder);
	};

	private WpCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("wp")
			.requires(source -> WaypointServer.isRunning() && Perms.check(source, Perms.Perm.USE))
			.executes(ctx -> help(ctx.getSource()))
			.then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
			.then(Commands.literal("add")
				// pos goes first: for "/wp add ~ ~ ~ Name" Brigadier keeps the first branch that parses.
				.then(Commands.argument("pos", BlockPosArgument.blockPos())
					.then(Commands.argument("name", StringArgumentType.greedyString())
						.executes(ctx -> add(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"), string(ctx, "name")))))
				.then(Commands.argument("name", StringArgumentType.greedyString())
					.executes(ctx -> add(ctx.getSource(), ctx.getSource().getPlayerOrException().blockPosition(), string(ctx, "name")))))
			.then(Commands.literal("list")
				.executes(ctx -> list(ctx.getSource(), null, 1))
				.then(Commands.argument("page", IntegerArgumentType.integer(1))
					.executes(ctx -> list(ctx.getSource(), null, IntegerArgumentType.getInteger(ctx, "page"))))
				.then(Commands.argument("category", StringArgumentType.word()).suggests(CATEGORIES)
					.executes(ctx -> list(ctx.getSource(), string(ctx, "category"), 1))
					.then(Commands.argument("page", IntegerArgumentType.integer(1))
						.executes(ctx -> list(ctx.getSource(), string(ctx, "category"), IntegerArgumentType.getInteger(ctx, "page"))))))
			.then(Commands.literal("near")
				.executes(ctx -> near(ctx.getSource(), 500))
				.then(Commands.argument("radius", IntegerArgumentType.integer(1, 1_000_000))
					.executes(ctx -> near(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
			.then(Commands.literal("info").then(ref().executes(ctx -> info(ctx.getSource(), resolve(ctx)))))
			.then(Commands.literal("get").then(ref().executes(ctx -> get(ctx.getSource(), resolve(ctx)))))
			.then(Commands.literal("share").then(ref().executes(ctx -> share(ctx.getSource(), resolve(ctx)))))
			.then(Commands.literal("tp").then(ref().executes(ctx -> report(ctx.getSource(),
				service().teleport(Actor.of(ctx.getSource()), resolve(ctx).id())))))
			.then(Commands.literal("set").then(quotedRef()
				.then(Commands.literal("color").then(Commands.argument("color", StringArgumentType.word()).suggests(COLORS)
					.executes(ctx -> {
						int color = Colors.parse(string(ctx, "color"));
						if (color < 0) {
							throw error("Нет такого цвета. Есть: " + String.join(", ", Colors.ids()));
						}
						return edit(ctx.getSource(), resolve(ctx), d -> d.color = color);
					})))
				.then(Commands.literal("category").then(Commands.argument("category", StringArgumentType.word()).suggests(CATEGORIES)
					.executes(ctx -> edit(ctx.getSource(), resolve(ctx), d -> d.category = string(ctx, "category")))))
				.then(Commands.literal("symbol").then(Commands.argument("symbol", StringArgumentType.greedyString())
					.executes(ctx -> edit(ctx.getSource(), resolve(ctx), d -> d.symbol = string(ctx, "symbol")))))
				.then(visibilityBranch())
				.then(Commands.literal("yaw")
					.then(Commands.literal("here").executes(ctx -> {
						int yaw = Math.round(Mth.wrapDegrees(ctx.getSource().getRotation().y));
						return edit(ctx.getSource(), resolve(ctx), d -> d.yaw = yaw);
					}))
					.then(Commands.literal("none").executes(ctx -> edit(ctx.getSource(), resolve(ctx), d -> d.yaw = null)))
					.then(Commands.argument("angle", IntegerArgumentType.integer(-360, 360))
						.executes(ctx -> edit(ctx.getSource(), resolve(ctx), d -> d.yaw = IntegerArgumentType.getInteger(ctx, "angle")))))
				.then(Commands.literal("y")
					.then(Commands.literal("none").executes(ctx -> edit(ctx.getSource(), resolve(ctx), d -> d.y = null)))
					.then(Commands.argument("y", IntegerArgumentType.integer())
						.executes(ctx -> edit(ctx.getSource(), resolve(ctx), d -> d.y = IntegerArgumentType.getInteger(ctx, "y")))))
				.then(Commands.literal("dimension").then(Commands.argument("dimension", DimensionArgument.dimension())
					.executes(ctx -> {
						String dimension = dimensionId(DimensionArgument.getDimension(ctx, "dimension"));
						return edit(ctx.getSource(), resolve(ctx), d -> d.dimension = dimension);
					})))))
			.then(Commands.literal("desc").then(quotedRef()
				.executes(ctx -> edit(ctx.getSource(), resolve(ctx), d -> d.description = ""))
				.then(Commands.argument("text", StringArgumentType.greedyString())
					.executes(ctx -> edit(ctx.getSource(), resolve(ctx), d -> d.description = string(ctx, "text"))))))
			.then(Commands.literal("move")
				.then(Commands.argument("pos", BlockPosArgument.blockPos()).then(ref()
					.executes(ctx -> move(ctx.getSource(), resolve(ctx), BlockPosArgument.getBlockPos(ctx, "pos")))))
				.then(ref().executes(ctx -> move(ctx.getSource(), resolve(ctx), ctx.getSource().getPlayerOrException().blockPosition()))))
			.then(Commands.literal("rename").then(quotedRef().then(Commands.argument("name", StringArgumentType.greedyString())
				.executes(ctx -> edit(ctx.getSource(), resolve(ctx), d -> d.name = string(ctx, "name"))))))
			.then(Commands.literal("remove").then(ref().executes(ctx -> remove(ctx.getSource(), resolve(ctx)))))
			.then(Commands.literal("restore").then(Commands.argument("ref", StringArgumentType.greedyString()).suggests(TRASH_REFS)
				.executes(ctx -> restore(ctx.getSource(), string(ctx, "ref")))))
			.then(Commands.literal("trash")
				.executes(ctx -> trash(ctx.getSource(), 1))
				.then(Commands.argument("page", IntegerArgumentType.integer(1))
					.executes(ctx -> trash(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page")))))
			.then(Commands.literal("history")
				.executes(ctx -> history(ctx.getSource(), null))
				.then(Commands.argument("ref", StringArgumentType.greedyString()).suggests(REFS)
					.executes(ctx -> history(ctx.getSource(), string(ctx, "ref")))))
			.then(Commands.literal("confirm").then(Commands.argument("token", StringArgumentType.word())
				.then(Commands.argument("category", StringArgumentType.word()).suggests(CATEGORIES)
					.executes(ctx -> report(ctx.getSource(), WaypointServer.running().shares()
						.confirm(Actor.of(ctx.getSource()), string(ctx, "token"), string(ctx, "category")))))))
			.then(categoryCommand())
			// "/wp import <dimension> [category] <file>": the file goes last so Xaero names like mw$default_1.txt need no quotes.
			.then(Commands.literal("import").requires(WpCommand::isAdmin)
				.then(Commands.argument("dimension", DimensionArgument.dimension())
					.then(Commands.argument("file", StringArgumentType.greedyString()).suggests(IMPORT_FILES)
						.executes(ctx -> importFile(ctx.getSource(), string(ctx, "file"), DimensionArgument.getDimension(ctx, "dimension"))))))
			.then(Commands.literal("export").requires(WpCommand::isAdmin)
				.then(Commands.argument("dimension", DimensionArgument.dimension())
					.executes(ctx -> export(ctx.getSource(), DimensionArgument.getDimension(ctx, "dimension")))))
			.then(Commands.literal("reload").requires(WpCommand::isAdmin)
				.executes(ctx -> {
					WaypointServer.reloadConfig();
					ctx.getSource().sendSuccess(() -> ChatUi.text("Конфиг Apollo Waypoints перечитан", ChatUi.GREEN), false);
					return 1;
				})));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> categoryCommand() {
		return Commands.literal("category")
			.then(Commands.literal("list").executes(ctx -> categories(ctx.getSource())))
			.then(Commands.literal("add").requires(WpCommand::isAdmin)
				.then(Commands.argument("id", StringArgumentType.word()).then(Commands.argument("name", StringArgumentType.greedyString())
					.executes(ctx -> report(ctx.getSource(), service().addCategory(Actor.of(ctx.getSource()), string(ctx, "id"), string(ctx, "name")))))))
			.then(Commands.literal("remove").requires(WpCommand::isAdmin)
				.then(Commands.argument("id", StringArgumentType.word()).suggests(CATEGORIES)
					.executes(ctx -> report(ctx.getSource(), service().removeCategory(Actor.of(ctx.getSource()), string(ctx, "id"))))))
			.then(Commands.literal("rename").requires(WpCommand::isAdmin)
				.then(Commands.argument("id", StringArgumentType.word()).suggests(CATEGORIES).then(Commands.argument("name", StringArgumentType.greedyString())
					.executes(ctx -> {
						Category c = category(ctx);
						return report(ctx.getSource(), service().updateCategory(Actor.of(ctx.getSource()),
							new Category(c.id(), string(ctx, "name").trim(), c.color(), c.visibility())));
					}))))
			.then(Commands.literal("color").requires(WpCommand::isAdmin)
				.then(Commands.argument("id", StringArgumentType.word()).suggests(CATEGORIES).then(Commands.argument("color", StringArgumentType.word()).suggests(COLORS)
					.executes(ctx -> {
						Category c = category(ctx);
						int color = Colors.parse(string(ctx, "color"));
						if (color < 0) {
							throw error("Нет такого цвета");
						}
						return report(ctx.getSource(), service().updateCategory(Actor.of(ctx.getSource()), new Category(c.id(), c.name(), color, c.visibility())));
					}))))
			.then(Commands.literal("visibility").requires(WpCommand::isAdmin)
				.then(Commands.argument("id", StringArgumentType.word()).suggests(CATEGORIES)
					.then(visibilityLiteral(Visibility.LOCAL)).then(visibilityLiteral(Visibility.GLOBAL)).then(visibilityLiteral(Visibility.MAP))));
	}

	private static ArgumentBuilder<CommandSourceStack, ?> visibilityLiteral(Visibility visibility) {
		return Commands.literal(visibility.id).executes(ctx -> {
			Category c = category(ctx);
			return report(ctx.getSource(), service().updateCategory(Actor.of(ctx.getSource()), new Category(c.id(), c.name(), c.color(), visibility)));
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> visibilityBranch() {
		LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal("visibility");
		for (Visibility visibility : Visibility.values()) {
			branch.then(Commands.literal(visibility.id).executes(ctx -> edit(ctx.getSource(), resolve(ctx), d -> d.visibility = visibility)));
		}
		return branch;
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> ref() {
		return Commands.argument("ref", StringArgumentType.greedyString()).suggests(REFS);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> quotedRef() {
		return Commands.argument("ref", StringArgumentType.string()).suggests(QUOTED_REFS);
	}

	// ---- handlers

	private static int help(CommandSourceStack source) {
		MutableComponent help = ChatUi.text("Apollo Waypoints — общие точки сервера. <точка> — имя или номер.", ChatUi.GOLD);
		// {shown, inserted on click, description}
		String[][] lines = {
			{"/wp add <имя>", "/wp add ", "точка на твоём месте"},
			{"/wp add <x y z> <имя>", "/wp add ~ ~ ~ ", "точка по координатам"},
			{"/wp list [категория]", "/wp list", "список с кнопками"},
			{"/wp near [радиус]", "/wp near", "что рядом и в какую сторону"},
			{"/wp info <точка>", "/wp info ", "подробно, с кнопками правки"},
			{"/wp set <точка> color|category|symbol|visibility|yaw|y|dimension …", "/wp set ", "изменить поле"},
			{"/wp move <точка>", "/wp move ", "перенести на твоё место"},
			{"/wp rename <точка> <имя>", "/wp rename ", "переименовать"},
			{"/wp desc <точка> <текст>", "/wp desc ", "описание"},
			{"/wp remove <точка>", "/wp remove ", "удалить (в корзину)"},
			{"/wp trash", "/wp trash", "корзина, оттуда можно вернуть"},
			{"/wp share <точка>", "/wp share ", "показать всем, у кого Xaero появится [Add]"},
			{"/wp get <точка>", "/wp get ", "добавить себе в Xaero"},
			{"/wp tp <точка>", "/wp tp ", "телепорт (опы и наблюдатели)"},
			{"/wp history [точка]", "/wp history", "кто что менял"},
			{"/wp category list", "/wp category list", "категории"},
		};
		for (String[] line : lines) {
			help.append("\n").append(ChatUi.suggest(line[0], ChatUi.AQUA, "Подставить команду", line[1]))
				.append(ChatUi.text(" — " + line[2], ChatUi.GRAY));
		}
		help.append("\n").append(ChatUi.text("Поделись точкой из Xaero (кнопка Share) — сервер предложит добавить её в общий список.", ChatUi.GRAY));
		source.sendSuccess(() -> help, false);
		return 1;
	}

	private static int add(CommandSourceStack source, BlockPos pos, String name) {
		WaypointDraft draft = new WaypointDraft();
		draft.name = name;
		draft.dimension = dimensionId(source.getLevel());
		draft.x = pos.getX();
		draft.y = pos.getY();
		draft.z = pos.getZ();
		Result result = service().create(Actor.of(source), draft);
		if (!result.ok()) {
			return report(source, result);
		}
		Waypoint w = result.waypoint();
		source.sendSuccess(() -> ChatUi.created(w, service().store().category(w.category()), canTeleport(source)), false);
		return 1;
	}

	private static int list(CommandSourceStack source, String categoryId, int page) throws CommandSyntaxException {
		Category category = null;
		if (categoryId != null) {
			category = service().store().category(categoryId);
			if (category == null) {
				throw error("Нет категории " + categoryId);
			}
		}
		String here = dimensionId(source.getLevel());
		String filter = categoryId;
		List<Waypoint> waypoints = live().stream()
			.filter(w -> filter == null || w.category().equals(filter))
			.sorted(Comparator.comparingInt((Waypoint w) -> dimensionOrder(w.dimension(), here)).thenComparing(w -> w.name().toLowerCase()))
			.toList();
		if (waypoints.isEmpty()) {
			source.sendSuccess(() -> ChatUi.text(filter == null ? "Точек пока нет. Добавь: /wp add <имя>" : "В этой категории точек нет", ChatUi.GRAY), false);
			return 0;
		}
		int pages = (waypoints.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		int current = Math.min(page, pages);
		boolean canTeleport = canTeleport(source);
		MutableComponent out = ChatUi.text((category == null ? "Точки" : "Точки · " + category.name()) + ": " + waypoints.size()
			+ (pages > 1 ? " (стр. " + current + "/" + pages + ")" : ""), ChatUi.GOLD);
		for (Waypoint w : waypoints.subList((current - 1) * PAGE_SIZE, Math.min(waypoints.size(), current * PAGE_SIZE))) {
			out.append("\n").append(ChatUi.row(w, service().store().category(w.category()), canTeleport));
		}
		if (pages > 1) {
			out.append("\n").append(ChatUi.pager(category == null ? "/wp list" : "/wp list " + category.id(), current, pages));
		}
		source.sendSuccess(() -> out, false);
		return waypoints.size();
	}

	private static int near(CommandSourceStack source, int radius) {
		String here = dimensionId(source.getLevel());
		Vec3 pos = source.getPosition();
		float yaw = source.getRotation().y;
		List<Waypoint> near = live().stream()
			.filter(w -> w.dimension().equals(here))
			.filter(w -> horizontalDistance(w, pos) <= radius)
			.sorted(Comparator.comparingDouble(w -> horizontalDistance(w, pos)))
			.limit(NEAR_LIMIT)
			.toList();
		if (near.isEmpty()) {
			source.sendSuccess(() -> ChatUi.text("В радиусе " + radius + " блоков точек нет", ChatUi.GRAY), false);
			return 0;
		}
		boolean canTeleport = canTeleport(source);
		MutableComponent out = ChatUi.text("Рядом (до " + radius + " блоков):", ChatUi.GOLD);
		for (Waypoint w : near) {
			double dx = w.x() + 0.5 - pos.x;
			double dz = w.z() + 0.5 - pos.z;
			// Minecraft yaw: 0 is south (+Z) and grows clockwise, so the target yaw is atan2(-dx, dz).
			double relative = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)) - yaw);
			String arrow = ARROWS[Math.floorMod(Math.round(relative / 45.0), 8)];
			MutableComponent row = ChatUi.name(w, service().store().category(w.category()))
				.append(ChatUi.text(" " + Math.round(horizontalDistance(w, pos)) + " м " + arrow + " ", ChatUi.YELLOW))
				.append(ChatUi.coordinates(w)).append(" ");
			out.append("\n").append(ChatUi.appendButtons(row, w, canTeleport));
		}
		source.sendSuccess(() -> out, false);
		return near.size();
	}

	private static int info(CommandSourceStack source, Waypoint w) {
		source.sendSuccess(() -> ChatUi.card(w, service().store().category(w.category()), canTeleport(source)), false);
		return 1;
	}

	private static int get(CommandSourceStack source, Waypoint w) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		player.sendSystemMessage(Component.literal(ChatUi.shareString(w)));
		if (!Syncer.hasXaero(player)) {
			source.sendSuccess(() -> ChatUi.text("Строка выше — формат Xaero's Minimap: с ним она превращается в кнопку [Add]. Координаты: "
				+ w.coordinates() + ", " + Dimensions.displayName(w.dimension()), ChatUi.GRAY), false);
		}
		return 1;
	}

	private static int share(CommandSourceStack source, Waypoint w) {
		String actor = Actor.of(source).name();
		Component line = Component.literal(ChatUi.shareString(w));
		for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
			player.sendSystemMessage(ChatUi.sharedBy(actor, w));
			if (Syncer.hasXaero(player)) {
				player.sendSystemMessage(line);
			}
		}
		if (source.getPlayer() == null) {
			source.sendSuccess(() -> ChatUi.text("Точка «" + w.name() + "» показана всем", ChatUi.GREEN), false);
		}
		return 1;
	}

	private static int move(CommandSourceStack source, Waypoint w, BlockPos pos) {
		String dimension = dimensionId(source.getLevel());
		return edit(source, w, d -> {
			d.x = pos.getX();
			d.y = pos.getY();
			d.z = pos.getZ();
			d.dimension = dimension;
		});
	}

	private static int remove(CommandSourceStack source, Waypoint w) {
		Result result = service().remove(Actor.of(source), w.id());
		if (!result.ok()) {
			return report(source, result);
		}
		source.sendSuccess(() -> ChatUi.text(result.message() + " ", ChatUi.GREEN)
			.append(ChatUi.run("Вернуть", ChatUi.AQUA, "Вернуть из корзины", "/wp restore " + w.id())), false);
		return 1;
	}

	private static int restore(CommandSourceStack source, String ref) throws CommandSyntaxException {
		Integer id = parseId(ref.trim());
		if (id == null) {
			// By name: the most recently deleted one.
			id = service().store().trash().stream()
				.filter(t -> t.waypoint().name().equalsIgnoreCase(ref.trim()))
				.max(Comparator.comparingLong(WaypointStore.Trashed::deletedAt))
				.map(t -> t.waypoint().id())
				.orElseThrow(() -> error("В корзине нет точки «" + ref.trim() + "»"));
		}
		return report(source, service().restore(Actor.of(source), id));
	}

	private static int trash(CommandSourceStack source, int page) {
		List<WaypointStore.Trashed> trash = service().store().trash().stream()
			.sorted(Comparator.comparingLong(WaypointStore.Trashed::deletedAt).reversed())
			.toList();
		if (trash.isEmpty()) {
			source.sendSuccess(() -> ChatUi.text("Корзина пуста", ChatUi.GRAY), false);
			return 0;
		}
		int pages = (trash.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		int current = Math.min(page, pages);
		MutableComponent out = ChatUi.text("Корзина: " + trash.size() + (pages > 1 ? " (стр. " + current + "/" + pages + ")" : "")
			+ ". Точки хранятся " + WaypointServer.config().trashDays + " дней.", ChatUi.GOLD);
		for (WaypointStore.Trashed t : trash.subList((current - 1) * PAGE_SIZE, Math.min(trash.size(), current * PAGE_SIZE))) {
			Waypoint w = t.waypoint();
			out.append("\n").append(ChatUi.text("■ ", Colors.rgb(w.color()))).append(ChatUi.text(w.name(), ChatUi.WHITE))
				.append(ChatUi.text(" #" + w.id() + " · " + w.coordinates() + " · удалил " + t.deletedBy() + " " + ChatUi.time(t.deletedAt()) + " ", ChatUi.GRAY))
				.append(ChatUi.run("Вернуть", ChatUi.AQUA, "Вернуть из корзины", "/wp restore " + w.id()));
		}
		if (pages > 1) {
			out.append("\n").append(ChatUi.pager("/wp trash", current, pages));
		}
		source.sendSuccess(() -> out, false);
		return trash.size();
	}

	private static int history(CommandSourceStack source, String ref) throws CommandSyntaxException {
		Integer id = null;
		if (ref != null) {
			id = parseId(ref.trim());
			if (id == null) {
				List<Waypoint> found = service().findByName(ref.trim());
				if (found.isEmpty()) {
					throw error("Точка «" + ref.trim() + "» не найдена; для удалённых укажи номер");
				}
				id = pick(source, found, ref.trim()).id();
			}
		}
		List<HistoryLog.Entry> entries = service().history().latest(id, PAGE_SIZE);
		if (entries.isEmpty()) {
			source.sendSuccess(() -> ChatUi.text("В журнале пусто", ChatUi.GRAY), false);
			return 0;
		}
		MutableComponent out = ChatUi.text(id == null ? "Последние изменения:" : "Изменения точки #" + id + ":", ChatUi.GOLD);
		for (HistoryLog.Entry entry : entries.reversed()) {
			out.append("\n").append(ChatUi.historyLine(entry));
		}
		source.sendSuccess(() -> out, false);
		return entries.size();
	}

	private static int categories(CommandSourceStack source) {
		MutableComponent out = ChatUi.text("Категории:", ChatUi.GOLD);
		for (Category c : service().store().categories()) {
			long count = live().stream().filter(w -> w.category().equals(c.id())).count();
			out.append("\n").append(ChatUi.text("■ ", Colors.rgb(c.color()))).append(ChatUi.text(c.name(), ChatUi.WHITE))
				.append(ChatUi.text(" (" + c.id() + ") · " + count + " точек · видимость: " + c.visibility().description + " ", ChatUi.GRAY))
				.append(ChatUi.run("список", ChatUi.AQUA, "Точки этой категории", "/wp list " + c.id()));
		}
		source.sendSuccess(() -> out, false);
		return 1;
	}

	/** {@code args} is "file" or "category file". */
	private static int importFile(CommandSourceStack source, String args, ServerLevel level) throws CommandSyntaxException {
		String fileName = args.trim();
		String category = "";
		int space = fileName.indexOf(' ');
		if (space > 0 && service().store().category(fileName.substring(0, space)) != null) {
			category = fileName.substring(0, space);
			fileName = fileName.substring(space + 1).trim();
		}
		Path dir = importDir();
		Path file = dir.resolve(fileName).normalize();
		if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
			throw error("Файла нет. Положи файл вейпоинтов Xaero (например mw$default_1.txt) в " + dir.toAbsolutePath().normalize());
		}
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw error("Не удалось прочитать " + fileName + ": " + e.getMessage());
		}
		String dimension = dimensionId(level);
		List<WaypointDraft> drafts = new ArrayList<>();
		for (XaeroFile.Entry e : XaeroFile.parse(lines)) {
			if (e.type() != XaeroFile.TYPE_NORMAL) {
				continue;
			}
			WaypointDraft d = new WaypointDraft();
			d.name = e.name();
			d.symbol = e.symbol();
			d.dimension = dimension;
			d.x = e.x();
			d.y = e.y();
			d.z = e.z();
			d.yaw = e.yaw();
			d.color = e.color();
			d.category = category;
			d.visibility = Visibility.fromXaeroType(e.visibilityType());
			drafts.add(d);
		}
		WaypointService.ImportSummary summary = service().importDrafts(Actor.of(source), drafts);
		MutableComponent out = ChatUi.text("Импорт " + fileName + " → " + Dimensions.displayName(dimension) + ": добавлено " + summary.added()
			+ ", пропущено " + summary.skipped() + " (такие имена уже есть), ошибок " + summary.errors().size(), ChatUi.GREEN);
		summary.errors().stream().limit(5).forEach(e -> out.append("\n").append(ChatUi.text(e, ChatUi.RED)));
		source.sendSuccess(() -> out, false);
		return summary.added();
	}

	private static int export(CommandSourceStack source, ServerLevel level) throws CommandSyntaxException {
		String dimension = dimensionId(level);
		List<XaeroFile.Entry> entries = live().stream()
			.filter(w -> w.dimension().equals(dimension))
			.map(w -> new XaeroFile.Entry(w.name(), w.symbol(), w.x(), w.y(), w.z(), w.color(), false, XaeroFile.TYPE_NORMAL,
				EXPORT_SET, w.yaw(), w.visibility().xaeroType()))
			.toList();
		Path file = service().store().dir().resolve("export").resolve(dimension.replace(':', '_').replace('/', '_') + ".txt");
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, XaeroFile.write(EXPORT_SET, entries), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw error("Не удалось записать " + file + ": " + e.getMessage());
		}
		source.sendSuccess(() -> ChatUi.text("Выгружено " + entries.size() + " точек (" + Dimensions.displayName(dimension)
			+ ") в формате Xaero, набор «" + EXPORT_SET + "»: " + file.toAbsolutePath().normalize(), ChatUi.GREEN), false);
		return entries.size();
	}

	// ---- helpers

	private static int edit(CommandSourceStack source, Waypoint w, Consumer<WaypointDraft> change) {
		WaypointDraft draft = w.draft();
		change.accept(draft);
		return report(source, service().update(Actor.of(source), w.id(), draft));
	}

	private static int report(CommandSourceStack source, Result result) {
		if (result.ok()) {
			source.sendSuccess(() -> ChatUi.text(result.message(), ChatUi.GREEN), false);
			return 1;
		}
		source.sendFailure(Component.literal(result.message()));
		return 0;
	}

	private static Waypoint resolve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String ref = string(ctx, "ref").trim();
		Integer id = parseId(ref);
		if (id != null) {
			Waypoint w = service().store().get(id);
			if (w == null) {
				throw error(service().store().getTrashed(id) != null ? "Точка #" + id + " в корзине: /wp restore " + id : "Точки #" + id + " нет");
			}
			return w;
		}
		List<Waypoint> found = service().findByName(ref);
		if (found.isEmpty()) {
			throw error("Точка «" + ref + "» не найдена");
		}
		return pick(ctx.getSource(), found, ref);
	}

	/** Several waypoints share the name (in different dimensions): take the one in the player's dimension. */
	private static Waypoint pick(CommandSourceStack source, List<Waypoint> found, String name) throws CommandSyntaxException {
		if (found.size() == 1) {
			return found.getFirst();
		}
		String here = dimensionId(source.getLevel());
		List<Waypoint> local = found.stream().filter(w -> w.dimension().equals(here)).toList();
		if (local.size() == 1) {
			return local.getFirst();
		}
		throw error("Точек «" + name + "» несколько: " + found.stream()
			.map(w -> "#" + w.id() + " (" + Dimensions.displayName(w.dimension()) + ")").collect(Collectors.joining(", ")) + ". Укажи номер.");
	}

	static Integer parseId(String ref) {
		String digits = ref.startsWith("#") ? ref.substring(1) : ref;
		if (digits.isEmpty() || digits.length() > 9 || !digits.chars().allMatch(Character::isDigit)) {
			return null;
		}
		return Integer.parseInt(digits);
	}

	private static Category category(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = string(ctx, "id");
		Category c = service().store().category(id);
		if (c == null) {
			throw error("Нет категории " + id);
		}
		return c;
	}

	private static boolean isAdmin(CommandSourceStack source) {
		return Perms.check(source, Perms.Perm.ADMIN);
	}

	private static boolean canTeleport(CommandSourceStack source) {
		return service().canTeleport(Actor.of(source));
	}

	private static String string(CommandContext<CommandSourceStack> ctx, String name) {
		return StringArgumentType.getString(ctx, name);
	}

	private static CommandSyntaxException error(String message) {
		return new SimpleCommandExceptionType(Component.literal(message)).create();
	}

	private static WaypointService service() {
		return WaypointServer.running().service();
	}

	private static List<Waypoint> live() {
		return List.copyOf(service().store().all());
	}

	private static Path importDir() {
		return service().store().dir().resolve("import");
	}

	static String dimensionId(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	private static int dimensionOrder(String dimension, String here) {
		if (dimension.equals(here)) {
			return 0;
		}
		return switch (dimension) {
			case Dimensions.OVERWORLD -> 1;
			case Dimensions.NETHER -> 2;
			case Dimensions.END -> 3;
			default -> 4;
		};
	}

	private static double horizontalDistance(Waypoint w, Vec3 pos) {
		double dx = w.x() + 0.5 - pos.x;
		double dz = w.z() + 0.5 - pos.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
