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
		if (Boolean.FALSE.toString().equals(System.getProperty(SHOW_HIDDEN_FIXED, "false"))) {
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
