package org.nlpcn.jcoder.run.mvc.processor;

import org.nutz.log.Log;
import org.nutz.log.Logs;
import org.nutz.mvc.ActionContext;
import org.nutz.mvc.ActionInfo;
import org.nutz.mvc.NutConfig;
import org.nutz.mvc.impl.processor.AbstractProcessor;

import javax.servlet.http.HttpServletResponse;

public class ApiCrossOriginProcessor extends AbstractProcessor {
	private static final Log log = Logs.get();

	private static final String origin = "*";
	private static final String methods = "get, post, put, delete, options";
	private static final String headers = "origin, content-type, accept, authorization";
	private static final String credentials = "true";
	private static final String maxAge = "3628800" ;

	@Override
	public void init(NutConfig config, ActionInfo ai) throws Throwable {
	}

	public void process(ActionContext ac) throws Throwable {
		HttpServletResponse resp = ac.getResponse();
		resp.addHeader("Access-Control-Allow-Origin", origin);
		resp.addHeader("Access-Control-Allow-Methods", methods);
		resp.addHeader("Access-Control-Allow-Headers", headers);
		resp.addHeader("Access-Control-Allow-Credentials", credentials);
		resp.addHeader("Access-Control-Max-Age", maxAge);

		if ("OPTIONS".equals(ac.getRequest().getMethod())) {
			if (log.isDebugEnabled())
				log.debugf("Feedback -- [%s] [%s] [%s] [%s]", origin, methods, headers, credentials);
		} else {
			doNext(ac);
		}
	}

}
