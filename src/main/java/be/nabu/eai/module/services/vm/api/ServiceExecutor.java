package be.nabu.eai.module.services.vm.api;

import javax.jws.WebParam;
import javax.jws.WebResult;

public interface ServiceExecutor {
	@WebResult(name = "output")
	public Object execute(@WebParam(name = "serviceId") String serviceId, @WebParam(name = "input") Object input);
}
