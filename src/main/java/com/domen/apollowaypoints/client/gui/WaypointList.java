package com.domen.apollowaypoints.client.gui;

import com.domen.apollowaypoints.client.ClientSettings;
import com.domen.apollowaypoints.client.ClientState;
import com.domen.apollowaypoints.model.Category;
import com.domen.apollowaypoints.model.Colors;
import com.domen.apollowaypoints.model.Dimensions;
import com.domen.apollowaypoints.model.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

/** Scrollable list of server waypoints: color, name and number, then coordinates, dimension and category. */
final class WaypointList extends ObjectSelectionList<WaypointList.Entry> {
	private final Consumer<Waypoint> onDoubleClick;
	private final Runnable onSelectionChanged;
	private String here = "";
	private Vec3 position;

	WaypointList(Minecraft minecraft, int width, int height, int y, Consumer<Waypoint> onDoubleClick, Runnable onSelectionChanged) {
		super(minecraft, width, height, y, 24);
		this.onDoubleClick = onDoubleClick;
		this.onSelectionChanged = onSelectionChanged;
	}

	@Override
	public int getRowWidth() {
		return Math.min(width - 24, 520);
	}

	void setWaypoints(List<Waypoint> waypoints, String here, Vec3 position) {
		Waypoint previous = selected();
		this.here = here;
		this.position = position;
		clearEntries();
		Entry keep = null;
		for (Waypoint w : waypoints) {
			Entry entry = new Entry(w);
			addEntry(entry);
			if (previous != null && previous.id() == w.id()) {
				keep = entry;
			}
		}
		super.setSelected(keep);
	}

	Waypoint selected() {
		Entry entry = getSelected();
		return entry == null ? null : entry.waypoint;
	}

	boolean isEmpty() {
		return children().isEmpty();
	}

	@Override
	public void setSelected(Entry entry) {
		super.setSelected(entry);
		onSelectionChanged.run();
	}

	final class Entry extends ObjectSelectionList.Entry<Entry> {
		final Waypoint waypoint;

		Entry(Waypoint waypoint) {
			this.waypoint = waypoint;
		}

		@Override
		public Component getNarration() {
			return Component.literal(waypoint.name());
		}

		@Override
		public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
			Font font = Minecraft.getInstance().font;
			int x = getContentX();
			int y = getContentY();
			int right = getContentRight();
			boolean hidden = ClientSettings.isHidden(waypoint.id());

			g.fill(x, y + 1, x + 8, y + 9, Ui.opaque(Colors.rgb(waypoint.color())));
			String distance = "";
			if (waypoint.dimension().equals(here) && position != null) {
				distance = Math.round(Math.sqrt(waypoint.distanceSq(position.x, position.y, position.z))) + " м";
				g.text(font, distance, right - font.width(distance), y + 1, Ui.YELLOW);
			}
			int titleWidth = right - (x + 12) - font.width(distance) - 6;
			g.text(font, font.plainSubstrByWidth(waypoint.name() + "  #" + waypoint.id(), titleWidth), x + 12, y + 1, hidden ? Ui.DARK_GRAY : Ui.WHITE);

			Category category = ClientState.category(waypoint.category());
			String info = waypoint.coordinates() + " · " + Dimensions.displayName(waypoint.dimension())
				+ " · " + (category == null ? waypoint.category() : category.name())
				+ (hidden ? " · скрыта" : "")
				+ (waypoint.description().isEmpty() ? "" : " · " + waypoint.description());
			g.text(font, font.plainSubstrByWidth(info, right - (x + 12)), x + 12, y + 12, Ui.GRAY);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			setSelected(this);
			if (doubleClick) {
				onDoubleClick.accept(waypoint);
			}
			return true;
		}
	}
}
