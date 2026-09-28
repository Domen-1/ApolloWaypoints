package com.domen.apollowaypoints.client.gui;

import com.domen.apollowaypoints.client.ApolloWaypointsClient;
import com.domen.apollowaypoints.client.ClientState;
import com.domen.apollowaypoints.client.Requests;
import com.domen.apollowaypoints.client.xaero.XaeroBridge;
import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Colors;
import com.domen.apollowaypoints.model.Dimensions;
import com.domen.apollowaypoints.model.Waypoint;
import com.domen.apollowaypoints.model.WaypointDraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Send from Xaero": the player's own waypoints of the current Xaero world, to tick and send to the server.
 * Ones whose name already exists on the server start unticked; the server skips name clashes anyway.
 */
public class XaeroImportScreen extends Screen {
	private final Screen parent;
	private final List<XaeroBridge.LocalWaypoint> local;
	private final Set<Integer> checked = new HashSet<>();
	private CycleButton<String> category;
	private Button sendButton;
	private Component status = Component.empty();
	private int statusColor = Ui.GRAY;
	private int pendingChunks;
	private final List<String> results = new ArrayList<>();

	public XaeroImportScreen(Screen parent) {
		super(Component.literal("Отправить точки из Xaero на сервер"));
		this.parent = parent;
		this.local = ApolloWaypointsClient.xaero().readLocal();
		for (int i = 0; i < local.size(); i++) {
			if (!existsOnServer(local.get(i).draft())) {
				checked.add(i);
			}
		}
	}

	@Override
	protected void init() {
		int margin = 10;
		List<String> categories = ClientState.categoryIds();
		String initial = category != null ? category.getValue()
			: categories.contains(Category.DEFAULT_ID) ? Category.DEFAULT_ID : categories.isEmpty() ? "" : categories.getFirst();
		category = addRenderableWidget(CycleButton.builder(XaeroImportScreen::categoryLabel, initial).withValues(categories)
			.create(width / 2 - 110, 22, 220, 20, Component.literal("В категорию")));

		ImportList list = new ImportList(minecraft, width, height - 60 - 48, 48);
		for (int i = 0; i < local.size(); i++) {
			list.add(i);
		}
		addRenderableWidget(list);

		int rowWidth = Math.min(width - 2 * margin, 480);
		int left = (width - rowWidth) / 2;
		int gap = 4;
		int quarter = (rowWidth - 3 * gap) / 4;
		int row = height - 26;
		addRenderableWidget(Button.builder(Component.literal("Все"), b -> {
			for (int i = 0; i < local.size(); i++) {
				checked.add(i);
			}
		}).bounds(left, row, quarter, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Ничего"), b -> checked.clear()).bounds(left + quarter + gap, row, quarter, 20).build());
		sendButton = addRenderableWidget(Button.builder(Component.literal("Отправить"), b -> send())
			.bounds(left + 2 * (quarter + gap), row, quarter, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Назад"), b -> onClose()).bounds(left + 3 * (quarter + gap), row, quarter, 20).build());
	}

	@Override
	public void tick() {
		sendButton.active = !checked.isEmpty() && pendingChunks == 0;
		sendButton.setMessage(Component.literal("Отправить (" + checked.size() + ")"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.centeredText(font, title, width / 2, 8, Ui.WHITE);
		if (local.isEmpty()) {
			g.centeredText(font, "В текущем мире Xaero нет своих точек. Точки берутся из измерения, где ты сейчас.", width / 2, 70, Ui.GRAY);
		}
		g.centeredText(font, status, width / 2, height - 40, statusColor);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void send() {
		List<WaypointDraft> drafts = new ArrayList<>();
		for (int i : checked.stream().sorted().toList()) {
			WaypointDraft d = local.get(i).draft().copy();
			d.category = category.getValue();
			drafts.add(d);
		}
		results.clear();
		pendingChunks = Requests.importAll(drafts, response -> {
			pendingChunks--;
			results.add(response.message());
			if (pendingChunks == 0) {
				status = Component.literal(String.join("; ", results));
				statusColor = results.stream().anyMatch(r -> r.contains("ошиб")) ? Ui.YELLOW : Ui.GREEN;
			}
		});
		status = Component.literal("Отправляю " + drafts.size() + " точек…");
		statusColor = Ui.GRAY;
	}

	private static boolean existsOnServer(WaypointDraft draft) {
		for (Waypoint w : ClientState.waypoints()) {
			if (w.dimension().equals(draft.dimension) && w.name().equalsIgnoreCase(draft.name.trim())) {
				return true;
			}
		}
		return false;
	}

	private static Component categoryLabel(String id) {
		Category c = ClientState.category(id);
		return Component.literal(c == null ? id : c.name());
	}

	private final class ImportList extends ObjectSelectionList<ImportList.Entry> {
		ImportList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, 24);
		}

		void add(int index) {
			addEntry(new Entry(index));
		}

		@Override
		public int getRowWidth() {
			return Math.min(width - 24, 480);
		}

		private final class Entry extends ObjectSelectionList.Entry<Entry> {
			private final int index;

			Entry(int index) {
				this.index = index;
			}

			@Override
			public Component getNarration() {
				return Component.literal(local.get(index).draft().name);
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
				Font font = Minecraft.getInstance().font;
				XaeroBridge.LocalWaypoint lw = local.get(index);
				WaypointDraft d = lw.draft();
				int x = getContentX();
				int y = getContentY();
				int right = getContentRight();
				boolean exists = existsOnServer(d);
				g.text(font, checked.contains(index) ? "☑" : "☐", x, y + 5, Ui.WHITE);
				g.fill(x + 14, y + 1, x + 22, y + 9, Ui.opaque(Colors.rgb(d.color)));
				g.text(font, font.plainSubstrByWidth(d.name, right - x - 26), x + 26, y + 1, exists ? Ui.DARK_GRAY : Ui.WHITE);
				String info = d.x + " " + (d.y == null ? "~" : d.y) + " " + d.z + " · " + Dimensions.displayName(d.dimension)
					+ " · набор «" + lw.set() + "»" + (exists ? " · уже есть на сервере" : "");
				g.text(font, font.plainSubstrByWidth(info, right - x - 26), x + 26, y + 12, Ui.GRAY);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				if (!checked.remove(index)) {
					checked.add(index);
				}
				return true;
			}
		}
	}
}
