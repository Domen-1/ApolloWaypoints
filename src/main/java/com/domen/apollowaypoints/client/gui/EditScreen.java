package com.domen.apollowaypoints.client.gui;

import com.domen.apollowaypoints.client.ClientState;
import com.domen.apollowaypoints.client.Requests;
import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Colors;
import com.domen.apollowaypoints.model.Dimensions;
import com.domen.apollowaypoints.model.Validation;
import com.domen.apollowaypoints.model.Visibility;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.model.WaypointDraft;
import com.domen.apollowaypoints.net.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Form for a new or existing waypoint. The server validates again; its answer is shown under the buttons. */
public class EditScreen extends Screen {
	private static final int ROW = 22;
	private static final int LABEL_WIDTH = 96;

	private record Label(String text, int y) {
	}

	private final Screen parent;
	private final Waypoint original;
	/** Values typed so far; kept when the window is resized and the widgets are rebuilt. */
	private WaypointDraft form;
	private final List<Label> labels = new ArrayList<>();

	private EditBox name;
	private EditBox symbol;
	private EditBox x;
	private EditBox y;
	private EditBox z;
	private EditBox yaw;
	private EditBox description;
	private CycleButton<Integer> color;
	private CycleButton<String> dimension;
	private CycleButton<String> category;
	private CycleButton<Visibility> visibility;
	private Button saveButton;
	private Component status = Component.empty();
	private int statusColor = Ui.GRAY;
	private int labelX;

	public EditScreen(Screen parent, Waypoint original) {
		super(Component.literal(original == null ? "Новая точка" : "Точка #" + original.id()));
		this.parent = parent;
		this.original = original;
	}

	@Override
	protected void init() {
		form = form == null ? initialDraft() : readForm();
		labels.clear();

		int formWidth = Math.min(340, width - 20);
		labelX = (width - formWidth) / 2;
		int fieldX = labelX + LABEL_WIDTH;
		int fieldWidth = formWidth - LABEL_WIDTH;
		// Title at y=6, status line at y=17, then 8 rows and the buttons; fits a 240px high GUI.
		int top = Math.max(30, (height - (8 * ROW + 26)) / 2);
		int row = top;

		name = box(fieldX, row, fieldWidth, form.name, Validation.MAX_NAME);
		label("Имя", row);
		row += ROW;

		symbol = box(fieldX, row, 40, form.symbol, 4);
		symbol.setHint(Component.literal(form.name.isEmpty() ? "" : Validation.defaultSymbol(form.name)));
		List<Integer> colors = new ArrayList<>();
		colors.add(-1);
		for (int i = 0; i < Colors.COUNT; i++) {
			colors.add(i);
		}
		color = addRenderableWidget(CycleButton.builder(EditScreen::colorLabel, form.color).withValues(colors)
			.create(fieldX + 46, row, fieldWidth - 46, 20, Component.literal("Цвет")));
		label("Символ", row);
		row += ROW;

		int coordinateWidth = (fieldWidth - 3 * 4 - 56) / 3;
		x = box(fieldX, row, coordinateWidth, String.valueOf(form.x), 9);
		y = box(fieldX + coordinateWidth + 4, row, coordinateWidth, form.y == null ? "" : String.valueOf(form.y), 6);
		y.setHint(Component.literal("~"));
		z = box(fieldX + 2 * (coordinateWidth + 4), row, coordinateWidth, String.valueOf(form.z), 9);
		addRenderableWidget(Button.builder(Component.literal("Здесь"), b -> fillPosition())
			.bounds(fieldX + fieldWidth - 56, row, 56, 20).build());
		label("X  Y  Z", row);
		row += ROW;

		dimension = addRenderableWidget(CycleButton.builder((String d) -> Component.literal(Dimensions.displayName(d)), form.dimension)
			.withValues(dimensionChoices()).create(fieldX, row, fieldWidth, 20, Component.literal("Измерение")));
		label("Измерение", row);
		row += ROW;

		List<String> categoryIds = ClientState.categoryIds();
		if (!categoryIds.contains(form.category) && !categoryIds.isEmpty()) {
			form.category = categoryIds.getFirst();
		}
		category = addRenderableWidget(CycleButton.builder(EditScreen::categoryLabel, form.category).withValues(categoryIds)
			.create(fieldX, row, fieldWidth, 20, Component.literal("Категория"), this::onCategoryChanged));
		label("Категория", row);
		row += ROW;

		visibility = addRenderableWidget(CycleButton.builder((Visibility v) -> Component.literal(v.description), form.visibility)
			.withValues(Visibility.values()).create(fieldX, row, fieldWidth, 20, Component.literal("Видна")));
		label("Видимость", row);
		row += ROW;

		yaw = box(fieldX, row, 60, form.yaw == null ? "" : String.valueOf(form.yaw), 4);
		yaw.setHint(Component.literal("нет"));
		addRenderableWidget(Button.builder(Component.literal("Как я смотрю"), b -> fillYaw())
			.bounds(fieldX + 64, row, fieldWidth - 64, 20).build());
		label("Поворот при ТП", row);
		row += ROW;

		description = box(fieldX, row, fieldWidth, form.description, Validation.MAX_DESCRIPTION);
		label("Описание", row);
		row += ROW + 4;

		int half = (formWidth - 4) / 2;
		saveButton = addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> save()).bounds(labelX, row, half, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> onClose()).bounds(labelX + half + 4, row, half, 20).build());
		setInitialFocus(name);
	}

	private EditBox box(int x, int y, int width, String value, int maxLength) {
		EditBox box = new EditBox(font, x, y, width, 20, Component.empty());
		box.setMaxLength(maxLength);
		box.setValue(value);
		return addRenderableWidget(box);
	}

	private void label(String text, int y) {
		labels.add(new Label(text, y + 6));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.centeredText(font, title, width / 2, 6, Ui.WHITE);
		for (Label label : labels) {
			g.text(font, label.text(), labelX, label.y(), Ui.GRAY);
		}
		g.centeredText(font, status, width / 2, 17, statusColor);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private WaypointDraft initialDraft() {
		if (original != null) {
			return original.draft();
		}
		WaypointDraft d = new WaypointDraft();
		LocalPlayer player = minecraft.player;
		if (player != null) {
			d.x = player.blockPosition().getX();
			d.y = player.blockPosition().getY();
			d.z = player.blockPosition().getZ();
		}
		if (minecraft.level != null) {
			d.dimension = minecraft.level.dimension().identifier().toString();
		}
		List<String> categories = ClientState.categoryIds();
		d.category = categories.contains(Category.DEFAULT_ID) ? Category.DEFAULT_ID : categories.isEmpty() ? "" : categories.getFirst();
		Category c = ClientState.category(d.category);
		d.visibility = c == null ? Visibility.LOCAL : c.visibility();
		d.color = -1;
		return d;
	}

	/** Raw widget values, before validation; used to survive a resize. */
	private WaypointDraft readForm() {
		WaypointDraft d = form.copy();
		d.name = name.getValue();
		d.symbol = symbol.getValue();
		Integer px = Ui.parseInt(x.getValue());
		Integer pz = Ui.parseInt(z.getValue());
		d.x = px == null ? d.x : px;
		d.z = pz == null ? d.z : pz;
		d.y = Ui.parseInt(y.getValue());
		d.yaw = Ui.parseInt(yaw.getValue());
		d.color = color.getValue();
		d.dimension = dimension.getValue();
		d.category = category.getValue();
		d.visibility = visibility.getValue();
		d.description = description.getValue();
		return d;
	}

	private List<String> dimensionChoices() {
		Set<String> choices = new LinkedHashSet<>(List.of(Dimensions.OVERWORLD, Dimensions.NETHER, Dimensions.END));
		if (minecraft.level != null) {
			choices.add(minecraft.level.dimension().identifier().toString());
		}
		choices.add(form.dimension);
		return List.copyOf(choices);
	}

	private void onCategoryChanged(CycleButton<String> button, String value) {
		// A new waypoint follows its category's visibility until the player picks one.
		Category c = ClientState.category(value);
		if (original == null && c != null) {
			visibility.setValue(c.visibility());
		}
	}

	private void fillPosition() {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		x.setValue(String.valueOf(player.blockPosition().getX()));
		y.setValue(String.valueOf(player.blockPosition().getY()));
		z.setValue(String.valueOf(player.blockPosition().getZ()));
		if (minecraft.level != null) {
			String here = minecraft.level.dimension().identifier().toString();
			if (dimensionChoices().contains(here)) {
				dimension.setValue(here);
			}
		}
	}

	private void fillYaw() {
		if (minecraft.player != null) {
			yaw.setValue(String.valueOf(Math.round(Mth.wrapDegrees(minecraft.player.getYRot()))));
		}
	}

	private void save() {
		WaypointDraft d = new WaypointDraft();
		d.name = name.getValue().trim();
		String error = Validation.checkName(d.name);
		if (error == null) {
			d.symbol = symbol.getValue().trim();
			error = Validation.checkSymbol(d.symbol);
		}
		Integer px = Ui.parseInt(x.getValue());
		Integer pz = Ui.parseInt(z.getValue());
		if (error == null && (px == null || pz == null)) {
			error = "X и Z — целые числа";
		}
		String yText = y.getValue().trim();
		Integer py = yText.isEmpty() || yText.equals("~") ? null : Ui.parseInt(yText);
		if (error == null && py == null && !(yText.isEmpty() || yText.equals("~"))) {
			error = "Y — целое число или пусто (точка без высоты)";
		}
		String yawText = yaw.getValue().trim();
		Integer pyaw = yawText.isEmpty() ? null : Ui.parseInt(yawText);
		if (error == null && pyaw == null && !yawText.isEmpty()) {
			error = "Поворот — целое число градусов или пусто";
		}
		if (error == null) {
			error = Validation.checkDescription(description.getValue().trim());
		}
		if (error != null) {
			setStatus(error, false);
			return;
		}
		d.x = px;
		d.y = py;
		d.z = pz;
		d.yaw = pyaw;
		d.dimension = dimension.getValue();
		d.category = category.getValue();
		d.color = color.getValue();
		d.visibility = visibility.getValue();
		d.description = description.getValue().trim();

		saveButton.active = false;
		setStatus("Сохраняю…", true);
		Consumer<Payloads.Response> done = response -> {
			saveButton.active = true;
			if (!response.ok()) {
				setStatus(response.message(), false);
				return;
			}
			if (parent instanceof WaypointsScreen list) {
				list.setStatus(response.message(), true);
			}
			minecraft.gui.setScreen(parent);
		};
		if (original == null) {
			Requests.create(d, done);
		} else {
			Requests.update(original.id(), d, done);
		}
	}

	private void setStatus(String text, boolean ok) {
		status = Component.literal(text);
		statusColor = ok ? Ui.GREEN : Ui.RED;
	}

	private static Component colorLabel(int index) {
		if (index < 0) {
			return Component.literal("как у категории");
		}
		return Component.literal("■ ").withColor(Colors.rgb(index)).append(Component.literal(Colors.name(index)));
	}

	private static Component categoryLabel(String id) {
		Category c = ClientState.category(id);
		return c == null ? Component.literal(id) : Component.literal("■ ").withColor(Colors.rgb(c.color())).append(Component.literal(c.name()));
	}
}
