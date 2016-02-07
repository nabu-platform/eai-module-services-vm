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
