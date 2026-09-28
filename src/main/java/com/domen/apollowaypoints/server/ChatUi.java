package com.domen.apollowaypoints.server;

import com.domen.apollowaypoints.format.XaeroShare;
import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Colors;
import com.domen.apollowaypoints.model.Dimensions;
import com.domen.apollowaypoints.model.Waypoint;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Chat messages of the /wp commands. Buttons use plain waypoint numbers so the commands work without quotes. */
final class ChatUi {
	static final int WHITE = 0xFFFFFF;
	static final int GRAY = 0xAAAAAA;
	static final int DARK_GRAY = 0x555555;
	static final int GREEN = 0x55FF55;
	static final int AQUA = 0x55FFFF;
	static final int YELLOW = 0xFFFF55;
	static final int RED = 0xFF5555;
	static final int GOLD = 0xFFAA00;

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

	private ChatUi() {
	}

	static MutableComponent text(String text, int rgb) {
		return Component.literal(text).withColor(rgb);
	}

	static String time(long millis) {
		return TIME.format(Instant.ofEpochMilli(millis));
	}

	static MutableComponent run(String label, int rgb, String hover, String command) {
		return Component.literal("[" + label + "]").withStyle(style -> style.withColor(rgb)
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))
			.withClickEvent(new ClickEvent.RunCommand(command)));
	}

	static MutableComponent suggest(String label, int rgb, String hover, String command) {
		return Component.literal("[" + label + "]").withStyle(style -> style.withColor(rgb)
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))
			.withClickEvent(new ClickEvent.SuggestCommand(command)));
	}

	static MutableComponent dimension(String dimension) {
		return text(Dimensions.displayName(dimension), Colors.rgb(Dimensions.colorIndex(dimension)));
	}

	static MutableComponent coordinates(Waypoint w) {
		return Component.literal(w.coordinates()).withStyle(style -> style.withColor(GRAY)
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("Нажми, чтобы скопировать")))
			.withClickEvent(new ClickEvent.CopyToClipboard(w.coordinates())));
	}

	/** "■ Name" in the waypoint's color; hover shows the details, click opens /wp info. */
	static MutableComponent name(Waypoint w, Category category) {
		return text("■ ", Colors.rgb(w.color())).append(Component.literal(w.name()).withStyle(style -> style.withColor(WHITE)
			.withHoverEvent(new HoverEvent.ShowText(details(w, category)))
			.withClickEvent(new ClickEvent.RunCommand("/wp info " + w.id()))));
	}

	static MutableComponent details(Waypoint w, Category category) {
		MutableComponent c = text(w.name(), Colors.rgb(w.color())).append(text(" #" + w.id(), DARK_GRAY))
			.append("\n").append(text(w.coordinates() + " · ", GRAY)).append(dimension(w.dimension()))
			.append("\n").append(text("Категория: " + (category == null ? w.category() : category.name()), GRAY));
		if (!w.description().isEmpty()) {
			c.append("\n").append(text(w.description(), WHITE));
		}
		return c.append("\n").append(text("Добавил " + w.ownerName() + ", " + time(w.createdAt()), DARK_GRAY));
	}

	/** One row of /wp list and friends. */
	static MutableComponent row(Waypoint w, Category category, boolean canTeleport) {
		MutableComponent row = name(w, category).append(text(" #" + w.id(), DARK_GRAY)).append(" ")
			.append(coordinates(w)).append(text(" · ", DARK_GRAY)).append(dimension(w.dimension())).append(" ");
		return appendButtons(row, w, canTeleport);
	}

	static MutableComponent appendButtons(MutableComponent line, Waypoint w, boolean canTeleport) {
		if (canTeleport) {
			line.append(run("ТП", AQUA, "Телепорт к точке", "/wp tp " + w.id())).append(" ");
		}
		return line.append(run("+X", GREEN, "Добавить себе в Xaero's Minimap", "/wp get " + w.id())).append(" ")
			.append(run("✎", YELLOW, "Подробнее и изменить", "/wp info " + w.id())).append(" ")
			.append(run("✖", RED, "Удалить (можно вернуть из корзины)", "/wp remove " + w.id()));
	}

	static MutableComponent card(Waypoint w, Category category, boolean canTeleport) {
		MutableComponent card = text("■ ", Colors.rgb(w.color())).append(text(w.name(), WHITE)).append(text(" #" + w.id(), DARK_GRAY))
			.append("\n").append(text("Координаты: ", GRAY)).append(coordinates(w)).append(text(" · ", DARK_GRAY)).append(dimension(w.dimension()))
			.append("\n").append(text("Категория: " + (category == null ? w.category() : category.name())
				+ " · цвет: " + Colors.name(w.color()) + " · символ: " + w.symbol(), GRAY))
			.append("\n").append(text("Видимость: " + w.visibility().description
				+ " · поворот при ТП: " + (w.yaw() == null ? "нет" : w.yaw() + "°"), GRAY));
		if (!w.description().isEmpty()) {
			card.append("\n").append(text("Описание: ", GRAY)).append(text(w.description(), WHITE));
		}
		card.append("\n").append(text("Добавил " + w.ownerName() + " " + time(w.createdAt())
			+ (w.updatedAt() != w.createdAt() ? " · изменил " + w.updatedBy() + " " + time(w.updatedAt()) : ""), DARK_GRAY));
		MutableComponent actions = Component.empty();
		if (canTeleport) {
			actions.append(run("ТП", AQUA, "Телепорт к точке", "/wp tp " + w.id())).append(" ");
		}
		actions.append(run("+Xaero", GREEN, "Добавить себе в Xaero's Minimap", "/wp get " + w.id())).append(" ")
			.append(run("Поделиться", GREEN, "Показать всем в чате", "/wp share " + w.id())).append(" ")
			.append(run("Перенести сюда", YELLOW, "Поставить точку на твоё место", "/wp move " + w.id())).append(" ")
			.append(run("Удалить", RED, "Удалить (можно вернуть из корзины)", "/wp remove " + w.id()));
		String id = String.valueOf(w.id());
		MutableComponent edit = text("Изменить: ", GRAY)
			.append(suggest("имя", YELLOW, "/wp rename " + id + " <новое имя>", "/wp rename " + id + " ")).append(" ")
			.append(suggest("цвет", YELLOW, "/wp set " + id + " color <цвет>", "/wp set " + id + " color ")).append(" ")
			.append(suggest("категорию", YELLOW, "/wp set " + id + " category <категория>", "/wp set " + id + " category ")).append(" ")
			.append(suggest("символ", YELLOW, "/wp set " + id + " symbol <1-2 знака>", "/wp set " + id + " symbol ")).append(" ")
			.append(suggest("видимость", YELLOW, "/wp set " + id + " visibility local|global|map", "/wp set " + id + " visibility ")).append(" ")
			.append(suggest("поворот", YELLOW, "/wp set " + id + " yaw here|none|<градусы>", "/wp set " + id + " yaw ")).append(" ")
			.append(suggest("описание", YELLOW, "/wp desc " + id + " <текст>", "/wp desc " + id + " "));
		return card.append("\n").append(actions).append("\n").append(edit);
	}

	static MutableComponent created(Waypoint w, Category category, boolean canTeleport) {
		MutableComponent line = text("Добавлена точка ", GREEN).append(name(w, category)).append(text(" #" + w.id(), DARK_GRAY))
			.append(" ").append(coordinates(w)).append(text(" · ", DARK_GRAY)).append(dimension(w.dimension())).append(" ");
		return appendButtons(line, w, canTeleport);
	}

	static MutableComponent announcement(String actor, Waypoint w) {
		return text(actor + " добавил точку ", GRAY).append(text("■ ", Colors.rgb(w.color()))).append(text(w.name(), WHITE))
			.append(text(" · " + w.coordinates() + " · ", GRAY)).append(dimension(w.dimension())).append(" ")
			.append(run("+X", GREEN, "Добавить себе в Xaero's Minimap", "/wp get " + w.id()));
	}

	/** The share line itself; Xaero replaces it with its own "[Add]" message. */
	static String shareString(Waypoint w) {
		return XaeroShare.build(w.name(), w.symbol(), w.x(), w.y(), w.z(), w.color(), w.yaw(), w.dimension());
	}

	static MutableComponent sharedBy(String actor, Waypoint w) {
		return text(actor + " делится точкой ", GRAY).append(text("■ ", Colors.rgb(w.color()))).append(text(w.name(), WHITE))
			.append(text(" · ", GRAY)).append(coordinates(w)).append(text(" · ", GRAY)).append(dimension(w.dimension()));
	}

	static MutableComponent sharePrompt(XaeroShare.Shared share, String token, List<Category> categories, Waypoint clash) {
		MutableComponent prompt = text("Добавить «" + share.name() + "» (" + share.x() + " " + (share.y() == null ? "~" : share.y())
			+ " " + share.z() + ", " + Dimensions.displayName(share.dimension()) + ") на сервер? ", GOLD);
		if (clash != null) {
			prompt.append(text("Точка с таким именем уже есть (#" + clash.id() + "), новая получит номер в имени. ", GRAY));
		}
		prompt.append(text("В категорию:", GRAY));
		for (Category category : categories) {
			prompt.append(" ").append(run(category.name(), Colors.rgb(category.color()), "Добавить в «" + category.name() + "»",
				"/wp confirm " + token + " " + category.id()));
		}
		return prompt;
	}

	static MutableComponent historyLine(HistoryLog.Entry e) {
		String verb = switch (e.action()) {
			case "created" -> "добавил";
			case "updated" -> "изменил";
			case "removed" -> "удалил";
			case "restored" -> "вернул";
			case "imported" -> "импортировал";
			case "category_added" -> "добавил категорию";
			case "category_updated" -> "изменил категорию";
			case "category_removed" -> "удалил категорию";
			default -> e.action();
		};
		String subject = e.action().startsWith("category_") ? e.name() : "«" + e.name() + "» #" + e.id();
		return text(time(e.time()) + " ", DARK_GRAY).append(text(e.actor() + " " + verb + " " + subject, WHITE))
			.append(e.details().isEmpty() ? Component.empty() : text(": " + e.details(), GRAY));
	}

	static MutableComponent pager(String command, int page, int pages) {
		MutableComponent pager = Component.empty();
		if (page > 1) {
			pager.append(run("« назад", AQUA, "Страница " + (page - 1), command + " " + (page - 1))).append(" ");
		}
		if (page < pages) {
			pager.append(run("дальше »", AQUA, "Страница " + (page + 1), command + " " + (page + 1)));
		}
		return pager;
	}
}
