/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.services.vm.util;

import be.nabu.eai.developer.MainController;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.libs.services.vm.step.Drop;
import be.nabu.libs.types.api.Element;
import javafx.event.EventHandler;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

public class DropWrapper {
	
	public static final String SHOW_HIDDEN_FIXED = "be.nabu.eai.developer.showHiddenDrops";
	private Drop drop;
	private TreeCell<Element<?>> cell;
	private ImageView image;

	public DropWrapper(TreeCell<Element<?>> cell, Drop drop) {
		this.cell = cell;
		this.drop = drop;
		draw();
	}
	
	private void draw() {
		image = MainController.loadGraphic("drop.png");
		image.setManaged(false);
		((Pane) cell.getTree().getParent()).getChildren().add(image);
		image.layoutXProperty().bind(cell.leftAnchorXProperty().subtract(10));
		// image is 16 pixels, we want to center it
		image.layoutYProperty().bind(cell.leftAnchorYProperty().subtract(8));
		// make invisible if it is not in scope
		if (Boolean.FALSE.toString().equals(System.getProperty(SHOW_HIDDEN_FIXED, "true"))) {
			image.visibleProperty().bind(cell.getNode().visibleProperty());
		}
		image.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				cell.show();
			}
		});
		Tooltip.install(image, new Tooltip(drop.getPath()));
	}
	
	public void remove() {
		((Pane) image.getParent()).getChildren().remove(image);
	}

	public ImageView getImage() {
		return image;
	}
}
