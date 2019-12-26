package be.nabu.eai.module.services.vm;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.repository.api.Entry;
import be.nabu.libs.services.vm.api.VMService;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuItem;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.AnchorPane;

public class ScreenshotContextMenu implements EntryContextMenuProvider {

	private Boolean allowScreenshotting = Boolean.parseBoolean(System.getProperty("vm.screenshot", "false"));
	
	@Override
	public MenuItem getContext(Entry entry) {
		if (allowScreenshotting && entry.isLeaf() && VMService.class.isAssignableFrom(entry.getNode().getArtifactClass())) {
			MenuItem item = new MenuItem("Screenshot");
			item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					AnchorPane pane = new AnchorPane();
					pane.setPrefWidth(1024);
					pane.setPrefHeight(1024);
					pane.setMinWidth(1024);
					pane.setMinHeight(1024);
					try {
						System.out.println("Screenshotting: " + entry.getId());
						VMService display = new VMServiceGUIManager().display(MainController.getInstance(), pane, entry);
						WritableImage snapshot = pane.snapshot(null, null);
//						BufferedImage image = SwingFXUtils.fromFXImage(snapshot, null);
//						ByteArrayOutputStream output = new ByteArrayOutputStream();
//						ImageIO.write(image, "png", output);
						ClipboardContent clipboard = new ClipboardContent();
						clipboard.putImage(snapshot);
//						clipboard.put(DataFormat.IMAGE, image);
						Clipboard.getSystemClipboard().setContent(clipboard);
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			return item;
		}
		return null;
	}
	
}
