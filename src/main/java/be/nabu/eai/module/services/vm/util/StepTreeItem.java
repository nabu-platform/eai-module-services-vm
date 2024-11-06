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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

import java.util.Set;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.module.services.vm.VMServiceGUIManager;
import be.nabu.jfx.control.tree.DisablableTreeItem;
import be.nabu.jfx.control.tree.MovableTreeItem;
import be.nabu.jfx.control.tree.RemovableTreeItem;
import be.nabu.jfx.control.tree.TreeItem;
import be.nabu.jfx.control.tree.TreeUtils;
import be.nabu.jfx.control.tree.TreeUtils.TreeItemCreator;
import be.nabu.libs.services.vm.step.LimitedStepGroup;
import be.nabu.libs.services.vm.step.Map;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;

public class StepTreeItem implements RemovableTreeItem<Step>, MovableTreeItem<Step>, DisablableTreeItem<Step> {
	private StepTreeItem parent;
	private BooleanProperty editableProperty = new SimpleBooleanProperty(false);
	private ObjectProperty<Step> itemProperty = new SimpleObjectProperty<Step>();
	private ObjectProperty<Node> graphicProperty = new SimpleObjectProperty<Node>();
	private BooleanProperty leafProperty = new SimpleBooleanProperty(false);
	private ObservableList<TreeItem<Step>> children = FXCollections.observableArrayList();
	private BooleanProperty disableProperty = new SimpleBooleanProperty(false);
	private ReadOnlyBooleanProperty hasLock;
	private Pane pane;
	
	public StepTreeItem(Pane pane, Step step, StepTreeItem parent, boolean isEditable, ReadOnlyBooleanProperty hasLock) {
		this.pane = pane;
		this.hasLock = hasLock;
		this.itemProperty.set(step);
		this.parent = parent;
		editableProperty.set(isEditable);
		disableProperty.set(step.isDisabled());
		refresh();
		disableProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> arg0, Boolean arg1, Boolean arg2) {
				if (arg2 != null) {
					itemProperty.get().setDisabled(arg2);
				}
			}
		});
	}

	@Override
	public BooleanProperty editableProperty() {
		return editableProperty;
	}

	@Override
	public ObservableList<TreeItem<Step>> getChildren() {
		return children;
	}
	
	@Override
	public void refresh() {
		leafProperty.set(!(itemProperty.get() instanceof StepGroup) || itemProperty.get() instanceof Map);
//		graphicProperty.set(MainController.loadGraphic(VMServiceGUIManager.getIcon(itemProperty.get())));
		ImageView loadGraphic = MainController.loadGraphic(VMServiceGUIManager.getIcon(itemProperty.get()));
		if (loadGraphic.getImage().getHeight() > 16) {
			loadGraphic.setPreserveRatio(true);
			loadGraphic.setFitHeight(16);
		}
		HBox box = new HBox();
		box.getChildren().add(loadGraphic);
		box.setAlignment(Pos.CENTER);
		box.setMinWidth(25);
		box.setMaxWidth(25);
		box.setPrefWidth(25);
		graphicProperty.set(box);
		if (!leafProperty.get()) {
			TreeUtils.refreshChildren(new TreeItemCreator<Step>() {
				@Override
				public TreeItem<Step> create(TreeItem<Step> parent, Step child) {
					return new StepTreeItem(pane, child, (StepTreeItem) parent, editableProperty.get(), hasLock);	
				}
			}, this, ((StepGroup) itemProperty.get()).getChildren());
		}
	}
	
	@Override
	public String getName() {
		return itemProperty.get().getClass().getSimpleName();
	}

	@Override
	public TreeItem<Step> getParent() {
		return parent;
	}

	@Override
	public ObjectProperty<Node> graphicProperty() {
		return graphicProperty;
	}

	@Override
	public ObjectProperty<Step> itemProperty() {
		return itemProperty;
	}

	@Override
	public BooleanProperty leafProperty() {
		return leafProperty;
	}

	private void renumber(Step stepToRenumber) {
		VMServiceUtils.renumber(stepToRenumber);
		Set<Node> lookupAll = pane.lookupAll(".vm-line-number");
		for (Node node : lookupAll) {
			Step step = (Step) node.getUserData();
			if (step.getLineNumber() != null) {
				((Label) node).setText("" + step.getLineNumber());
			}
		}
	}
	
	@Override
	public boolean remove() {
		if ((hasLock == null || hasLock.get()) && itemProperty().get().getParent() != null) {
			itemProperty().get().getParent().getChildren().remove(itemProperty().get());
			renumber(itemProperty.get());
			MainController.getInstance().setChanged();
			return true;
		}
		return false;
	}

	@Override
	public TreeItem<Step> move(be.nabu.jfx.control.tree.MovableTreeItem.Direction direction) {
		if (hasLock == null || hasLock.get()) {
			Step step = itemProperty().get();
			if (step.getParent() != null) {
				int indexInParent = step.getParent().getChildren().indexOf(step);
				switch(direction) {
					case DOWN:
						// can only move it down if it's not the last item
						if (indexInParent < step.getParent().getChildren().size() - 1) {
							step.getParent().getChildren().remove(indexInParent);
							step.getParent().getChildren().add(indexInParent + 1, step);
							renumber(step);
							getParent().refresh();
						}
					break;
					case UP:
						if (indexInParent > 0) {
							step.getParent().getChildren().remove(indexInParent);
							step.getParent().getChildren().add(indexInParent - 1, step);
							renumber(step);
							getParent().refresh();
						}
					break;
					case RIGHT:
						// it will be added to the _previous_ step group
						if (indexInParent > 0) {
							Step targetParent = step.getParent().getChildren().get(indexInParent - 1);
							if (targetParent instanceof StepGroup) {
								if (targetParent instanceof LimitedStepGroup && ((LimitedStepGroup) targetParent).getAllowedSteps().contains(step.getClass())) {
									step.getParent().getChildren().remove(indexInParent);
									((StepGroup) targetParent).getChildren().add(step);
									step.setParent((StepGroup) targetParent);
									getParent().getChildren().get(indexInParent - 1).refresh();
									renumber(step);
									getParent().refresh();
								}
							}
						}
					break;
					case LEFT:
						if (step.getParent().getParent() != null) {
							StepGroup targetParent = step.getParent().getParent();
							int indexInNewParent = targetParent.getChildren().indexOf(step.getParent());
							if (targetParent instanceof LimitedStepGroup && ((LimitedStepGroup) targetParent).getAllowedSteps().contains(step.getClass())) {
								step.getParent().getChildren().remove(indexInParent);
								targetParent.getChildren().add(indexInNewParent + 1, step);
								step.setParent(targetParent);
								renumber(step);
								getParent().getParent().refresh();
								getParent().refresh();
							}
						}
					break;
				}
				MainController.getInstance().setChanged();
			}
		}
		return null;
	}

	@Override
	public BooleanProperty disableProperty() {
		return disableProperty;
	}
	
	@Override
	public String toString() {
		return "Step (" + itemProperty.get().toString() + ")";
	}
}
