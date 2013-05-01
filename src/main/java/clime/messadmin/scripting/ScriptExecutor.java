/**
 *
 */
package clime.messadmin.scripting;

import java.beans.ConstructorProperties;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import clime.messadmin.admin.AdminActionProvider;
import clime.messadmin.admin.BaseAdminActionProvider;
import clime.messadmin.admin.BaseAdminActionWithContext;
import clime.messadmin.i18n.I18NSupport;
import clime.messadmin.model.Application;
import clime.messadmin.model.ApplicationInfo;
import clime.messadmin.model.Server;
import clime.messadmin.providers.spi.ApplicationDataProvider;
import clime.messadmin.providers.spi.ServerDataProvider;
import clime.messadmin.utils.StringUtils;

/**
 * Execute javax.script live!
 * @author C&eacute;drik LIME
 */
public class ScriptExecutor extends BaseAdminActionProvider implements ServerDataProvider, ApplicationDataProvider, AdminActionProvider {
	private static final String BUNDLE_NAME = ScriptExecutor.class.getName();

	private static final String ACTION_ID = "script";//$NON-NLS-1$
	private static final String SCRIPT = "script";//$NON-NLS-1$
	private static final String SCRIPT_ENGINE_NAME = "scriptEngineName";//$NON-NLS-1$
	private static final String DEFAULT_SCRIPT_ENGINE_NAME = "JavaScript";//$NON-NLS-1$

	@ConstructorProperties({})
	public ScriptExecutor() {
		super();
	}

	/** {@inheritDoc} */
	@Override
	public String getApplicationDataTitle(ServletContext context) {
		return getServerDataTitle();
	}

	/** {@inheritDoc} */
	@Override
	public String getServerDataTitle() {
		return I18NSupport.getLocalizedMessage(BUNDLE_NAME, "title");//$NON-NLS-1$
	}

	/** {@inheritDoc} */
	@Override
	public String getXHTMLApplicationData(ServletContext context) {
		String urlPrefix = "?" + ACTION_PARAMETER_NAME + '=' + getActionID();
		if (context != null) {
			urlPrefix += "&" + BaseAdminActionWithContext.CONTEXT_KEY + '=' + urlEncodeUTF8(Server.getInstance().getInternalContext(context));
		}
		final StringBuilder xhtml = new StringBuilder(256);
		xhtml.append("<form action=\"").append(urlPrefix).append("\" method=\"post\" target=\"_blank\">\n");

		// Script TextArea
		xhtml.append("	<label>").append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, "script")).append("<br />");//$NON-NLS-2$
		xhtml.append("	<textarea id=\"").append(SCRIPT).append("\" name=\"").append(SCRIPT).append("\" rows=\"10\" cols=\"80\">print('Hello, World!\\n'); // prints on the System Console</textarea>");
		xhtml.append("</label><br />\n");

		// Script Engine Name
		xhtml.append("	<label>").append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, "script_engine_name")).append("&nbsp;");//$NON-NLS-2$
//		xhtml.append("<input type=\"text\" id=\"").append(SCRIPT_ENGINE_NAME).append("\" name=\"").append(SCRIPT_ENGINE_NAME).append("\" value=\"").append(DEFAULT_SCRIPT_ENGINE_NAME).append("\" />");
		xhtml.append("<select id=\"").append(SCRIPT_ENGINE_NAME).append("\" name=\"").append(SCRIPT_ENGINE_NAME).append("\">\n");
		for (ScriptEngineFactory scriptEngineFactory : getScriptEngineManager(context).getEngineFactories()) {
			String engineName      = scriptEngineFactory.getEngineName();
			String engineVersion   = scriptEngineFactory.getEngineVersion();
			String languageName    = scriptEngineFactory.getLanguageName();
			String languageVersion = scriptEngineFactory.getLanguageVersion();
			String id = scriptEngineFactory.getNames().get(0);
			String label = languageName+'/'+languageVersion + " ("+engineName+'/'+engineVersion+')';
			String title = I18NSupport.getLocalizedMessage(BUNDLE_NAME, "aliases") + ' ';//$NON-NLS-1$
			for (Iterator<String> iter = scriptEngineFactory.getNames().iterator(); iter.hasNext();) {
				String name = iter.next();
				title += name;
				if (iter.hasNext()) {
					title += ", ";
				}
			}
			xhtml.append("	<option label=\"").append(StringUtils.escapeXml(label)).append("\" title=\"").append(StringUtils.escapeXml(title)).append("\">").append(StringUtils.escapeXml(id)).append("</option>\n");
		}
		xhtml.append("</select>\n");
		xhtml.append("</label>\n");

		// Submit button / AJAX call
		String formParamsJS = "'"+SCRIPT_ENGINE_NAME+"='+ encodeURIComponent(document.getElementById('"+SCRIPT_ENGINE_NAME+"').value) + " +
				"'&"+SCRIPT+"='+ encodeURIComponent(document.getElementById('"+SCRIPT+"').value)";
		xhtml.append("	&nbsp;&nbsp;<input type=\"submit\" onclick=\"jah('").append(urlPrefix).append("','").append(getActionID()).append("-result").append("','post',").append(formParamsJS).append(");return false;\"/>\n");
		xhtml.append("</form>\n");

		// Empty div to display script execution output
		xhtml.append("<div id=\"").append(getActionID()).append("-result").append("\"></div>");
		return xhtml.toString();
	}

	/** {@inheritDoc} */
	@Override
	public String getXHTMLServerData() {
		return getXHTMLApplicationData(null);
	}

	/** {@inheritDoc} */
	@Override
	public int getPriority() {
		return 0;
	}

	/** {@inheritDoc} */
	@Override
	public String getActionID() {
		return ACTION_ID;
	}

	protected ScriptEngineManager getScriptEngineManager(String context) {
		ScriptEngineManager scriptEngineManager = null;
		if (context != null) {
			Application application = Server.getInstance().getApplication(context);
			if (application != null) {
				ClassLoader cl = application.getApplicationInfo().getClassLoader();
				scriptEngineManager = new ScriptEngineManager(cl);
			}
		}
		if (scriptEngineManager == null) {
			scriptEngineManager = new ScriptEngineManager();
		}
		return scriptEngineManager;
	}
	protected ScriptEngineManager getScriptEngineManager(ServletContext context) {
		if (context != null) {
			String internalContext = Server.getInstance().getInternalContext(context);
			return getScriptEngineManager(internalContext);
		} else {
			return getScriptEngineManager((String) null);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String scriptEngineName = request.getParameter(SCRIPT_ENGINE_NAME);
		if (scriptEngineName == null || "".equals(scriptEngineName.trim())) {
			scriptEngineName = DEFAULT_SCRIPT_ENGINE_NAME;
		}
		String script = request.getParameter(SCRIPT);
		if (script == null || "".equals(script.trim())) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, SCRIPT);
			return;
		} //else
		String context = BaseAdminActionWithContext.getContext(request);
		ScriptEngineManager seManager = getScriptEngineManager(context);
		ScriptEngine sEngine = seManager.getEngineByName(scriptEngineName);
		if (sEngine == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, scriptEngineName);
			return;
		}
		// expose object as named variable to script
		// if/when running in an application context, expose the ServletContext!
		// Get the WebApp context we want to work with
		sEngine.put("request", request); // current request
		sEngine.put("response", response); // current response
		HttpSession session = request.getSession(false);
		sEngine.put("session", session); // current session, can be null
		if (context != null) { // "remote" ServletContext, not the current one!
			ServletContext servletContext = ((ApplicationInfo)Server.getInstance().getApplication(context).getApplicationInfo()).getServletContext();
			sEngine.put("servletContext", servletContext);
			sEngine.put("application", servletContext);
		}
		Object result;
		try {
			result = sEngine.eval(script);
		} catch (ScriptException se) {
			result = se;
		} catch (RuntimeException rte) {
			result = rte;
		}
		response.setContentType("text/plain");//$NON-NLS-1$
		setNoCache(response);
		PrintWriter out = response.getWriter();
		out.print(result == null ? "" : result);// no need to escape (StringUtils.escapeXml(String.valueOf(result)));), since text/plain
		out.flush();
		out.close();
	}

}
