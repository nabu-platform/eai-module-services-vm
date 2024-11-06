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

import java.util.Arrays;
import java.util.LinkedHashSet;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.ArtifactGUIInstance;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.developer.api.ValidatableArtifactGUIInstance;
import be.nabu.eai.developer.components.RepositoryBrowser;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.repository.api.ContainerArtifact;
import be.nabu.eai.repository.api.Entry;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;

public class FindStepContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		if (!entry.isLeaf()) {
			MenuItem item = new MenuItem("Find Step By Id");
			item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@SuppressWarnings({ "unchecked", "rawtypes" })
				@Override
				public void handle(ActionEvent arg0) {
					SimplePropertyUpdater updater = new SimplePropertyUpdater(true, new LinkedHashSet(Arrays.asList(new SimpleProperty<String>("Step Id", String.class, true))));
					EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Insert Values", new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							String id = updater.getValue("Step Id");
							if (id != null) {
								Entry findInEntry = findInEntry(entry, id);
								if (findInEntry != null) {
									Tab tab = MainController.getInstance().getTab(findInEntry.getId());
									if (tab == null) {
										MainController.getInstance().open(findInEntry.getId());
//										RepositoryBrowser.open(MainController.getInstance(), findInEntry);
									}
									else {
										MainController.getInstance().activate(findInEntry.getId());
									}
									ArtifactGUIInstance artifactInstance = MainController.getInstance().getArtifactInstance(findInEntry.getId());
									if (artifactInstance instanceof ValidatableArtifactGUIInstance) {
										ValidationMessage validation = new ValidationMessage(Severity.INFO, "Finding the step: " + id);
										validation.setContext(Arrays.asList(id));
										((ValidatableArtifactGUIInstance) artifactInstance).locate(validation);
									}
								}
							}
						}
					});
				}
			});
			return item;
		}
		return null;
	}
	
	private static Entry findInEntry(Entry entry, String stepId) {
		try {
			if (entry.isNode() && VMService.class.isAssignableFrom(entry.getNode().getArtifactClass())) {
				Step step = getStep(((VMService) entry.getNode().getArtifact()).getRoot(), stepId);
				if (step != null) {
					return entry;
				}
			}
			else if (entry.isNode() && ContainerArtifact.class.isAssignableFrom(entry.getNode().getArtifactClass())) {
				for (Artifact artifact : ((ContainerArtifact) entry.getNode().getArtifact()).getContainedArtifacts()) {
					if (VMService.class.isAssignableFrom(artifact.getClass())) {
						Step step = getStep(((VMService) artifact).getRoot(), stepId);
						if (step != null) {
							return entry;
						}
					}
				}
			}
		}
		catch (Exception e) {
			// ignore
		}
		for (Entry child : entry) {
			Entry findInEntry = findInEntry(child, stepId);
			if (findInEntry != null) {
				return findInEntry;
			}
		}
		return null;
	}
	
	private static Step getStep(StepGroup sequence, String stepId) {
		for (Step child : sequence.getChildren()) {
			if (stepId.equals(child.getId())) {
				return child;
			}
			if (child instanceof StepGroup) {
				Step found = getStep((StepGroup) child, stepId);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}
}
