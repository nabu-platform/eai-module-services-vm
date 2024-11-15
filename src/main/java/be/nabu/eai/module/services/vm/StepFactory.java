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

package be.nabu.eai.module.services.vm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Callback;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.module.services.vm.util.VMServiceUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.jfx.control.tree.TreeCellValue;
import be.nabu.jfx.control.tree.TreeItem;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.step.Break;
import be.nabu.libs.services.vm.step.Catch;
import be.nabu.libs.services.vm.step.For;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Map;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.services.vm.step.Switch;
import be.nabu.libs.services.vm.step.Throw;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class StepFactory implements Callback<TreeItem<Step>, TreeCellValue<Step>> {
	
	private ObservableList<Validation<?>> validations;

	public StepFactory(ObservableList<Validation<?>> validations) {
		this.validations = validations;
	}
	
	@Override
	public TreeCellValue<Step> call(TreeItem<Step> treeItem) {
		return new StepCell(treeItem, validations);
	}
	
	public static class StepCell implements TreeCellValue<Step> {

		private ObjectProperty<TreeCell<Step>> cell = new SimpleObjectProperty<TreeCell<Step>>();
		private HBox box = new HBox();
		private HBox validationsBox = new HBox();
		private List<Validation<?>> localMessages = new ArrayList<Validation<?>>();
		private ListChangeListener<Validation<?>> listChangeListener;
		private TextField textField = new TextField();
		private Step step;
		private boolean editingComment, editingLabel;
		
		public StepCell(TreeItem<Step> treeItem, ObservableList<Validation<?>> validations) {
			this.step = treeItem.itemProperty().get();
			refresh(treeItem.itemProperty().get());
			listChangeListener = new ListChangeListener<Validation<?>>() {
				@Override
				public void onChanged(ListChangeListener.Change<? extends Validation<?>> change) {
					while (change.next()) {
						if (change.wasRemoved()) {
							for (Validation<?> value : change.getRemoved()) {
								StepCell.this.localMessages.remove(value);
							}
						}
						if (change.wasAdded()) {
							for (Validation<?> value : change.getAddedSubList()) {
								for (Object context : value.getContext()) {
									if (context.toString().contains(treeItem.itemProperty().get().getId())) {
										StepCell.this.localMessages.add(value);
									}
								}
							}
						}
						if (change.wasUpdated() || change.wasReplaced()) {
							throw new RuntimeException("Not expecting updated or replaced: " + change.getList());
						}
					}
					drawValidations(treeItem.itemProperty().get());
				}
			};
			validations.addListener(listChangeListener);
			for (Validation<?> value : validations) {
				for (Object context : value.getContext()) {
					if (context.toString().contains(treeItem.itemProperty().get().getId())) {
						localMessages.add(value);
					}
				}
			}
			if (!localMessages.isEmpty()) {
				drawValidations(treeItem.itemProperty().get());
			}
			box.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
				@Override
				public void handle(KeyEvent event) {
					if (event.getCode() == KeyCode.F2 && !event.isMetaDown()) {
						editingComment = true;
						editingLabel = false;
						edit();
						event.consume();
					}
					else if (event.getCode() == KeyCode.F3 && !event.isMetaDown()) {
						editingComment = false;
						editingLabel = true;
						edit();
						event.consume();
					}
				}
			});
			textField.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
				@Override
				public void handle(KeyEvent event) {
					if (event.getCode() == KeyCode.ENTER) {
						MainController.getInstance().setChanged();
						String value = textField.getText() == null || textField.getText().trim().isEmpty() ? null : textField.getText();
						if (editingComment) {
							step.setComment(value);
						}
						else {
							step.setLabel(value);
						}
						editingComment = false;
						editingLabel = false;
						refresh(step);
						box.requestFocus();
						event.consume();
					}
					else if (event.getCode() == KeyCode.ESCAPE) {
						editingComment = false;
						editingLabel = false;
						refresh(step);
						box.requestFocus();
						event.consume();
					}
				}
			});
			textField.getStyleClass().add("editableTextfield");
			textField.setPromptText("Add a description");
		}
		
		private void edit() {
			textField.setText(editingComment ? step.getComment() : step.getLabel());
			box.getChildren().clear();
			box.getChildren().add(textField);
			textField.requestFocus();
			textField.selectAll();
		}
		
		@Override
		public Region getNode() {
			return box;
		}

		@Override
		public ObjectProperty<TreeCell<Step>> cellProperty() {
			return cell;
		}

		@Override
		public void refresh() {
			if (cell.get() != null) {
				Step step = cell.get().getItem().itemProperty().get();
				refresh(step);
			}
		}

		private HBox drawValidations(Step step) {
			validationsBox.getChildren().clear();
			String message = null;
			Severity severity = null;
			for (Validation<?> validation : localMessages) {
				if (validation.getSeverity() == Severity.INFO) {
					continue;
				}
				if (severity == null || validation.getSeverity().ordinal() > severity.ordinal()) {
					severity = validation.getSeverity();
				}
				if (message == null) {
					message = "";
				}
				else {
					message += "\n";
				}
				message += validation.getMessage();
			}
			if (severity != null) {
				Node graphic = MainController.loadFixedSizeGraphic("severity-" + severity.name().toLowerCase() + ".png");
				Tooltip tooltip = new Tooltip(message);
				Tooltip.install(graphic, tooltip);
				validationsBox.getChildren().add(graphic);
			}
			return validationsBox;
		}
		
		private void refresh(Step step) {
			box.getChildren().clear();
			box.getChildren().add(validationsBox);
			
			drawStep(step, box);
		}

		public static void drawStep(Step step, HBox box) {
			String labelText = step.getLabel();
			if (labelText == null && step.getParent() instanceof Switch) {
				labelText = "$default";
			}
			
			box.setAlignment(Pos.CENTER_LEFT);
			
			boolean preamble = false;
			
			// normally we always want this! otherwise it's not easy to make it reactive
			// if you turn it off, we never want it, reactivity doesn't matter
			if (System.getProperty("blox.lineNumber", "true").equals("true")) {
				Label label = new Label("" + step.getLineNumber());
				label.getStyleClass().add("vm-line-number");
				label.setUserData(step);
				box.getChildren().addAll(label);
				preamble = true;
			}
			
			if (step.getFeatures() != null) {
				Label label = new Label(step.getFeatures());
				label.getStyleClass().add("vm-feature");
				box.getChildren().addAll(label);
				if (preamble) {
					label.getStyleClass().add("vm-margin-left");
				}
				preamble = true;
			}
			
			if (labelText != null) {
				Label label = new Label(labelText);
				label.getStyleClass().add("vm-label");
				box.getChildren().addAll(label);
				if (preamble) {
					label.getStyleClass().add("vm-margin-left");
				}
			}
			
			String comment = step.getComment();
			if (comment == null && step instanceof Map) {
				List<Invoke> invokes = new ArrayList<Invoke>();
				for (Step child : ((Map) step).getChildren()) {
					if (child instanceof Invoke) {
						invokes.add((Invoke) child);
					}
				}
				// make sure they are ordered chronologically for the comment
				invokes.sort(new Comparator<Invoke>() {
					@Override
					public int compare(Invoke arg0, Invoke arg1) {
						return arg0.getInvocationOrder() - arg1.getInvocationOrder();
					}
				});
				if (!invokes.isEmpty()) {
					// in case we have the same service multiple times but without templating (so the same comment), we don't want to repeat it
					List<String> comments = new ArrayList<String>();
					StringBuilder commentBuilder = new StringBuilder();
					for (Invoke invoke : invokes) {
						String invokeComment = VMServiceUtils.templateServiceComment(invoke);
						if (invokeComment == null) {
							invokeComment = invoke.getServiceId();
						}
						if (!comments.contains(invokeComment)) {
							comments.add(invokeComment);
						}
					}
					for (int i = 0; i < comments.size(); i++) {
						if (i > 0) {
							if (i < comments.size() - 1) {
								commentBuilder.append(", ");
							}
							else {
								commentBuilder.append(" and ");
							}
						}
						commentBuilder.append(i > 0 ? comments.get(i).substring(0, 1).toLowerCase() + comments.get(i).substring(1) : comments.get(i));
					}
					comment = commentBuilder.toString();
				}
			}
			
			// only show the step name if explicitly set or if there is no comment and label present
			if (step instanceof For || step.getName() != null || (step.getLabel() == null && comment == null) || (labelText == null && comment == null) || step instanceof Switch || (step instanceof Throw && ((Throw) step).getMessage() == null)) {
				Label name = new Label(step.getName() == null ? step.getClass().getSimpleName() : step.getName());
				name.getStyleClass().add("vm-name");
				box.getChildren().addAll(name);
			}
			
			if (step instanceof For) {
				boolean hasVariable = false;
				if (((For) step).getVariable() != null) {
					hasVariable = true;
					Label each = new Label("each");
					each.getStyleClass().add("vm-description");
					if (step.getLabel() != null || preamble) {
						each.getStyleClass().add("vm-margin-left");
					}
					Label item = new Label(((For) step).getVariable());
					item.getStyleClass().add("vm-value");
					box.getChildren().addAll(each, item);
				}
				if (((For) step).getQuery() != null) {
					Label in = new Label(hasVariable ? "in" : "each");
					in.getStyleClass().add("vm-description");
					
					Label list = new Label(((For) step).getQuery());
					list.getStyleClass().add("vm-value");
					box.getChildren().addAll(in, list);
				}
				if (((For) step).getInto() != null) {
					Label in = new Label("into");
					in.getStyleClass().add("vm-description");
					Label list = new Label(((For) step).getInto());
					list.getStyleClass().add("vm-value");
					box.getChildren().addAll(in, list);
				}
			}
			else if (step instanceof Switch) {
				if (((Switch) step).getQuery() != null) {
					Label on = new Label("on");
					on.getStyleClass().add("vm-description");
					
					Label query = new Label(((Switch) step).getQuery());
					query.getStyleClass().add("vm-value");
					
					box.getChildren().addAll(on, query);
				}
			}
			else if (step instanceof Throw) {
				if (((Throw) step).getCode() != null) {
					Label code = new Label("[" + ((Throw) step).getCode() + "]");
					code.getStyleClass().addAll("vm-value", "vm-throw-code");
					box.getChildren().addAll(code);
				}
				if (((Throw) step).getMessage() != null) {
					Label message = new Label(((Throw) step).getMessage());
					message.getStyleClass().addAll("vm-value", "vm-throw-message");
					box.getChildren().addAll(message);
					
					if (((Throw) step).getCode() == null && (step.getLabel() != null || preamble)) {
						message.getStyleClass().add("vm-margin-left");
					}
				}
			}
			else if (step instanceof Catch) {
				if (((Catch) step).getCodes() != null) {
					String result = null;
					for (String single : ((Catch) step).getCodes()) {
						if (single != null) {
							if (result == null) {
								result = "";
							}
							else {
								result += ", ";
							}
							result += single;
						}
					}
					if (result != null) {
						Label code = new Label("[" + result + "]");
						code.getStyleClass().addAll("vm-value", "vm-throw-code");
						box.getChildren().addAll(code);
					}
				}
			}
			else if (step instanceof Link) {
				Label from = new Label(stripFrom(((Link) step).getFrom()));
				from.getStyleClass().addAll("vm-value", "vm-link-from");
				if (((Link) step).isFixedValue()) {
					from.getStyleClass().add("vm-fixed-value");
				}
				
				Label pointer = new Label("->");
				pointer.getStyleClass().add("vm-margin-left");
				
				Label to = new Label(((Link) step).getTo());
				to.getStyleClass().addAll("vm-value", "vm-link-to");
				to.getStyleClass().add("vm-margin-left");
				
				box.getChildren().addAll(from, pointer, to);
			}
			else if (step instanceof Break) {
				Step breakTarget = step;
				int counter = ((Break) step).getCount();
				while (counter > 0) {
					breakTarget = breakTarget.getParent();
					if (breakTarget == null) {
						break;
					}
					// only these types actually decrease the break count
					if (breakTarget instanceof Sequence || breakTarget instanceof For) {
						counter--;
					}
				}
				Label code = new Label("[" + ((Break) step).getCount() + "]");
				code.getStyleClass().addAll("vm-value", "vm-throw-code");
				boolean continueExecution  = ((Break) step).getContinueExecution() != null && ((Break) step).getContinueExecution();
				Label outOf = new Label(continueExecution ? "continue with" : "out of");
				outOf.getStyleClass().add("vm-description");
				box.getChildren().addAll(code, outOf);
					
				String name = breakTarget == null ? "INVALID" : breakTarget.getName();
				if (name == null) {
					name = breakTarget.getClass().getSimpleName().replaceAll("^.*\\.", "");
				}
				
				Label query = new Label(name);
				query.getStyleClass().add("vm-value");
				
				box.getChildren().addAll(query);
			}
			
			if (comment != null) {
				Label label = new Label(comment);
				label.getStyleClass().add("vm-comment");
				if (labelText != null || preamble) {
					label.getStyleClass().add("vm-margin-left");
				}
				if (comment.contains("TODO")) {
					label.getStyleClass().add("vm-comment-todo");
				}
				box.getChildren().addAll(label);
			}
		}
		
		private static String stripFrom(String from) {
			if (from.matches("^result[\\w]{32}/.*")) {
				return from.substring(39);
			}
			return from;
		}
		
	}
	
}
