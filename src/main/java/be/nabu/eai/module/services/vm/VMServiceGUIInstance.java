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

import java.io.IOException;
import java.util.List;

import javafx.scene.layout.AnchorPane;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.RefresheableArtifactGUIInstance;
import be.nabu.eai.developer.api.ValidatableArtifactGUIInstance;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.validator.api.Validation;

public class VMServiceGUIInstance implements RefresheableArtifactGUIInstance, ValidatableArtifactGUIInstance {

	private Entry entry;
	private VMService service;
	private boolean changed;
	private VMServiceGUIManager manager;
	
	public VMServiceGUIInstance(VMServiceGUIManager manager) {
		// delayed
		this.manager = manager;
	}
	
	public VMServiceGUIInstance(VMServiceGUIManager manager, Entry entry, VMService service) {
		this.manager = manager;
		this.entry = entry;
		this.service = service;
	}

	@Override
	public String getId() {
		return entry.getId();
	}

	@Override
	public List<Validation<?>> save() throws IOException {
		manager.validate(service);
		return new VMServiceManager().save((RepositoryEntry) entry, service);
	}

	@Override
	public boolean hasChanged() {
		return changed;
	}

	@Override
	public boolean isReady() {
		return entry != null && service != null;
	}

	public Entry getEntry() {
		return entry;
	}

	public void setEntry(Entry entry) {
		this.entry = entry;
	}

	public VMService getService() {
		return service;
	}

	public void setService(VMService service) {
		this.service = service;
	}

	@Override
	public boolean isEditable() {
		return entry.isEditable();
	}

	@Override
	public void setChanged(boolean changed) {
		if (service != null) {
			manager.validate(service);
		}
		this.changed = changed;
	}

	@Override
	public void refresh(AnchorPane pane) {
		entry.refresh(true);
		try {
			this.service = manager.display(MainController.getInstance(), pane, entry);
		}
		catch (Exception e) {
			throw new RuntimeException("Could not refresh: " + getId(), e);
		}
	}

	@Override
	public List<? extends Validation<?>> validate() {
		return new VMServiceManager().validate(service);
//		return service.getRoot().validate(EAIResourceRepository.getInstance().getServiceContext());
	}

	@Override
	public boolean locate(Validation<?> validation) {
		return manager.locate(validation);
	}

	@Override
	public Artifact getArtifact() {
		return service;
	}
	
	@Override
	public boolean requiresPropertiesPane() {
		return true;
	}
}
