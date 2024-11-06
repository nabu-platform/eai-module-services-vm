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

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.ClipboardProvider;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.libs.validator.api.ValidationMessage.Severity;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;

public class VMClipboardProvider implements ClipboardProvider<Step> {

	@Override
	public String getDataType() {
		return VMServiceGUIManager.DATA_TYPE_STEP;
	}

	@Override
	public String serialize(Step instance) {
		Sequence sequence = new Sequence();
		sequence.getChildren().add(instance);
		ByteBuffer buffer = IOUtils.newByteBuffer();
		try {
			VMServiceManager.formatSequence(buffer, sequence);
			return new String(IOUtils.toBytes(buffer), "UTF-8");
		}
		catch (IOException e) {
			MainController.getInstance().notify(new ValidationMessage(Severity.ERROR, "Can not copy step"));
			return null;
		}
	}

	@Override
	public Step deserialize(String content) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Class<Step> getClipboardClass() {
		return Step.class;
	}

}
