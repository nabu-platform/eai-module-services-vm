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

import java.util.ArrayList;
import java.util.List;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.module.services.vm.VMServiceGUIManager;
import be.nabu.eai.module.services.vm.VMServiceManager;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.jfx.control.tree.TreeCell;
import be.nabu.jfx.control.tree.clipboard.ClipboardHandler;
import be.nabu.jfx.control.tree.drag.TreeDragDrop;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.step.LimitedStepGroup;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;
import be.nabu.utils.io.IOUtils;

public class StepClipboardHandler implements ClipboardHandler {

	private Tree<Step> tree;

	public StepClipboardHandler(Tree<Step> serviceTree) {
		this.tree = serviceTree;
	}

	@Override
	public ClipboardContent getContent() {
		List<Step> elements = new ArrayList<Step>();
		for (TreeCell<Step> entry : tree.getSelectionModel().getSelectedItems()) {
			elements.add(entry.getItem().itemProperty().get());
		}
		return MainController.buildClipboard(elements.toArray());
	}

	@Override
	public void setClipboard(Clipboard arg0) {
		if (!tree.getSelectionModel().getSelectedItems().isEmpty()) {
			TreeCell<Step> target = tree.getSelectionModel().getSelectedItems().get(0);
			if (target.getItem().itemProperty().get() instanceof StepGroup) {
				StepGroup targetStep = (StepGroup) target.getItem().itemProperty().get();
				String serializedStep = (String) arg0.getContent(TreeDragDrop.getDataFormat(VMServiceGUIManager.DATA_TYPE_STEP));
				if (serializedStep != null) {
					try {
						Sequence sequence = VMServiceManager.parseSequence(IOUtils.wrap(serializedStep.getBytes("UTF-8"), true));
						Step step = sequence.getChildren().get(0);
						boolean isAllowed = true;
						if (targetStep instanceof LimitedStepGroup) {
							isAllowed &= ((LimitedStepGroup) targetStep).getAllowedSteps().contains(step.getClass());
						}
						if (isAllowed) {
							targetStep.getChildren().add(step);
							step.setParent(targetStep);
							MainController.getInstance().setChanged();
						}
						target.refresh();
					} 
					catch (Exception e) {
						MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "Can not paste step"));
					}
				}
			}
		}
	}

}
