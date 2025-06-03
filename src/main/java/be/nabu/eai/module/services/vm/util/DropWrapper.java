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

import java.util.List;
import java.util.Set;

import be.nabu.eai.developer.MainController;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.services.vm.step.Drop;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.validator.api.ValidationMessage;
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
	private String sourceId;

	public DropWrapper(TreeCell<Element<?>> cell, Drop drop, String sourceId) {
		this.cell = cell;
		this.drop = drop;
		this.sourceId = sourceId;
		draw();
	}
	
	private void draw() {
		image = MainController.loadGraphic("drop.png");
		image.setManaged(false);
		// allow key events
		image.setFocusTraversable(true);
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
				image.requestFocus();
				// I don't want to rebuild the logic for links, just reuse it, should refactor in the future
				Link tmp = new Link();
				tmp.setTo(drop.getPath());
				tmp.setFixedValue(true);
				MainController.getInstance().showProperties(new LinkPropertyUpdater(tmp, null, MainController.getInstance().getRepository(), sourceId) {
					@Override
					public List<ValidationMessage> updateProperty(Property<?> property, Object value) {
						List<ValidationMessage> updateProperty = super.updateProperty(property, value);
						drop.setPath(tmp.getTo());
						return updateProperty;
					}

					@Override
					public Set<Property<?>> getSupportedProperties() {
						return super.getBasicProperties();
					}
					
				});
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

	public TreeCell<Element<?>> getCell() {
		return cell;
	}

	public Drop getDrop() {
		return drop;
	}
	
}
