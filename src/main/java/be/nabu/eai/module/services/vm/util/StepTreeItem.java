package be.nabu.eai.module.services.vm.util;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
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
	
	public StepTreeItem(Step step, StepTreeItem parent, boolean isEditable) {
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
		graphicProperty.set(MainController.loadGraphic(VMServiceGUIManager.getIcon(itemProperty.get())));
		if (!leafProperty.get()) {
			TreeUtils.refreshChildren(new TreeItemCreator<Step>() {
				@Override
				public TreeItem<Step> create(TreeItem<Step> parent, Step child) {
					return new StepTreeItem(child, (StepTreeItem) parent, editableProperty.get());	
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

	@Override
	public boolean remove() {
		if (itemProperty().get().getParent() != null) {
			itemProperty().get().getParent().getChildren().remove(itemProperty().get());
			MainController.getInstance().setChanged();
			return true;
		}
		return false;
	}

	@Override
	public TreeItem<Step> move(be.nabu.jfx.control.tree.MovableTreeItem.Direction direction) {
		Step step = itemProperty().get();
		if (step.getParent() != null) {
			int indexInParent = step.getParent().getChildren().indexOf(step);
			switch(direction) {
				case DOWN:
					// can only move it down if it's not the last item
					if (indexInParent < step.getParent().getChildren().size() - 1) {
						step.getParent().getChildren().remove(indexInParent);
						step.getParent().getChildren().add(indexInParent + 1, step);
						getParent().refresh();
					}
				break;
				case UP:
					if (indexInParent > 0) {
						step.getParent().getChildren().remove(indexInParent);
						step.getParent().getChildren().add(indexInParent - 1, step);
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
							getParent().getParent().refresh();
							getParent().refresh();
						}
					}
				break;
			}
			MainController.getInstance().setChanged();
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
