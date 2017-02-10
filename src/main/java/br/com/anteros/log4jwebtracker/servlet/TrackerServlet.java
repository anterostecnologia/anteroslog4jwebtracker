package br.com.anteros.log4jwebtracker.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import br.com.anteros.log4jwebtracker.io.StreamUtils;
import br.com.anteros.log4jwebtracker.logging.LoggingUtils;

/**
 * Tracker servlet.
 *
 * @author Mariano Ruiz
 */
public class TrackerServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Logger logger = Logger.getLogger(TrackerServlet.class);

	private static final int BUFFER_SIZE = 1024 * 16;

	private byte[] jqueryMin = null;
	private byte[] jqueryWordWrap = null;
	private byte[] css = null;
	private byte[] logo = null;
	private byte[] favicon = null;

	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		// Action URI, eg. /webtracker/tracker/config
		String action = request.getRequestURI();
		// Servlet URI, eg. /webtracker/tracker
		String baseAction = request.getContextPath() + request.getServletPath();
		if (request.getPathInfo() == null || request.getPathInfo().equals("") || request.getPathInfo().equals("/")) {
			response.sendRedirect(response.encodeRedirectURL(baseAction + "/config"));
			// If JS resource
		} else if (request.getPathInfo().startsWith("/js/")) {
			if (request.getPathInfo().equals("/js/jquery-1.6.4.min.js")) {
				doResource(request, response, getJQueryMin(), "application/javascript");
			} else if (request.getPathInfo().equals("/js/jquery.wordWrap.js")) {
				doResource(request, response, getJQueryWordWrap(), "application/javascript");
			} else {
				logger.warn("Request javascript resource " + action + " not found.");
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			}
			// If CSS resource
		} else if (request.getPathInfo().startsWith("/css/")) {
			if (request.getPathInfo().equals("/css/tracker.css")) {
				doResource(request, response, getCSS(), "text/css");
			} else {
				logger.warn("Request CSS resource " + action + " not found.");
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			}
			// If image resource
		} else if (request.getPathInfo().startsWith("/img/")) {
			if (request.getPathInfo().equals("/img/anteros_logo.png")) {
				doResource(request, response, getLogo(), "image/png");
			} else if (request.getPathInfo().equals("/img/favicon.ico")) {
				doResource(request, response, getFavicon(), "image/x-icon");
			} else {
				logger.warn("Request image resource " + action + " not found.");
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			}
			// If log request
		} else if (request.getPathInfo().startsWith("/taillog")) {
			doTailLog(request, response, action, baseAction);
			// If ajax log request
		} else if (request.getPathInfo().startsWith("/getlog")) {
			doGetLog(request, response, action, baseAction);
			// Page resource
		} else {
			doPage(request, response, action, baseAction);
		}
	}

	private void doResource(
			HttpServletRequest request, HttpServletResponse response,
			byte[] buffer, String contentType)
			throws ServletException, IOException {
		ServletOutputStream output = response.getOutputStream();
		response.setContentType(contentType);
		response.setContentLength(buffer.length);
		output.write(buffer, 0, buffer.length);
		output.flush();
		output.close();
	}

	private void doPage(
			HttpServletRequest request, HttpServletResponse response,
			String action, String baseAction)
			throws ServletException, IOException {
		request.setAttribute("action", action);
		request.setAttribute("baseAction", baseAction);
		if (request.getPathInfo().equals("/log")) {
			doLog(request, response, action, baseAction);
		} else if (request.getPathInfo().equals("/config")) {
			doConfiguration(request, response, action, baseAction);
		} else {
			logger.warn("Request page " + action + " not found.");
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	private void doConfiguration(
			HttpServletRequest request, HttpServletResponse response,
			String action, String baseAction)
			throws ServletException, IOException {
		List loggers = LoggingUtils.getLoggers();
		request.setAttribute("loggers", loggers);
		Enumeration e = request.getParameterNames();
		while (e.hasMoreElements()) {
			String parameterName = (String) e.nextElement();
			if (parameterName.equals("root")) {
				Level level = Level.toLevel(request.getParameter(parameterName));
				Logger root = LogManager.getRootLogger();
				synchronized (root) {
					root.setLevel(level);
				}
				if (logger.isDebugEnabled()) {
					logger.debug(parameterName + '=' + level.toString());
				}
			} else {
				if (LoggingUtils.contains(loggers, parameterName)) {
					Level level = Level.toLevel(request.getParameter(parameterName));
					Logger logg = LogManager.getLogger(parameterName);
					synchronized (logg) {
						logg.setLevel(level);
					}
					if (logger.isDebugEnabled()) {
						logger.debug(parameterName + '=' + level.toString());
					}
				} else {
					logger.warn("Logger name " + parameterName + " not exist.");
				}
			}
		}
		// getServletConfig().getServletContext()
		// .getRequestDispatcher("/tracker.jsp")
		// .forward(request, response);
		doHTML(request, response);
	}

	private void doLog(
			HttpServletRequest request, HttpServletResponse response,
			String action, String baseAction)
			throws ServletException, IOException {
		request.setAttribute("fileAppenders", LoggingUtils.getFileAppenders());
		// getServletConfig().getServletContext()
		// .getRequestDispatcher("/tracker.jsp")
		// .forward(request, response);
		doHTML(request, response);
	}

	private void doTailLog(HttpServletRequest request,
			HttpServletResponse response, String action, String baseAction)
			throws ServletException, IOException {
		String appenderName = request.getParameter("appender");
		if (appenderName != null) {
			int lines = 20;
			if (request.getParameter("lines") != null) {
				try {
					lines = Integer.parseInt(request.getParameter("lines"));
				} catch (NumberFormatException e) {
					logger.warn("Number format 'lines' parameter invalid = "
							+ request.getParameter("lines"));
				}
			}
			FileAppender fileAppender = LoggingUtils.getFileAppender(appenderName);
			if (fileAppender != null) {
				OutputStream output = response.getOutputStream();
				try {
					String contentType = "text/plain";
					if (fileAppender.getEncoding() != null) {
						contentType += "; charset=" + fileAppender.getEncoding();
					}
					response.setContentType(contentType);
					response.setHeader("Cache-Control", "no-cache"); // HTTP 1.1
					response.setHeader("Pragma", "no-cache"); // HTTP 1.0
					response.setDateHeader("Expires", -1); // prevents caching
					RandomAccessFile inputFile = new RandomAccessFile(fileAppender.getFile(), "r");
					StreamUtils.tailFile(inputFile, output, BUFFER_SIZE, lines);
					inputFile.close();
				} catch (IOException e) {
					logger.error("Error getting the file appender="
							+ fileAppender.getFile(), e);
					output.write("TrackerError: Check the log manually.".getBytes());
				}
				output.flush();
				output.close();
			} else {
				logger.error("FileAppender with name=" + appenderName + " not exist.");
			}
		} else {
			logger.error("No appender name parameter specified.");
		}
	}

	private void doGetLog(HttpServletRequest request,
			HttpServletResponse response, String action, String baseAction)
			throws ServletException, IOException {
		String appenderName = request.getParameter("appender");
		if (appenderName != null) {
			FileAppender fileAppender = LoggingUtils.getFileAppender(appenderName);
			if (fileAppender != null) {
				File file = new File(fileAppender.getFile());
				OutputStream output = response.getOutputStream();
				try {
					String contentType = "text/plain";
					if (fileAppender.getEncoding() != null) {
						contentType += "; charset=" + fileAppender.getEncoding();
					}
					response.setContentType(contentType);
					response.setContentLength((int) file.length());
					response.setHeader("Cache-Control", "no-cache"); // HTTP 1.1
					response.setHeader("Pragma", "no-cache"); // HTTP 1.0
					response.setDateHeader("Expires", -1); // prevents caching
					response.setHeader("Content-Disposition",
							"attachment; filename=\""
									+ file.getName() + "\"");
					InputStream fileStream = new FileInputStream(fileAppender.getFile());
					StreamUtils.readStream(fileStream, output, BUFFER_SIZE);
					fileStream.close();
				} catch (IOException e) {
					response.setHeader("Content-Disposition", "");
					logger.error("Error getting the file appender="
							+ fileAppender.getFile(), e);
					output.write("TrackerError: Check the log manually.".getBytes());
					output.close();
				}
				output.flush();
				output.close();
			} else {
				logger.error("FileAppender with name=" + appenderName + " not exist.");
			}
		} else {
			logger.error("No appender name parameter specified.");
		}
	}

	public void doHTML(HttpServletRequest request, HttpServletResponse response)
			throws java.io.IOException, ServletException {

		ServletOutputStream out = response.getOutputStream();

		response.setContentType("text/html; charset=ISO-8859-1");

		out.println("<!DOCTYPE html>");
		out.println("<html>");
		out.println("	<head>");
		out.println("		<title>Anteros Log4j Web Tracker</title>");
		out.println();
		out.println("		<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
		out.print("		<link rel=\"shortcut icon\" href=\"");
		out.print((String) request.getAttribute("baseAction"));
		out.println("/img/favicon.ico\" type=\"image/x-icon\">");
		out.print("		<link rel=\"icon\" href=\"");
		out.print((String) request.getAttribute("baseAction"));
		out.println("/img/favicon.ico\" type=\"image/x-icon\">");
		out.print("		<link rel=\"stylesheet\" type=\"text/css\" href=\"");
		out.print((String) request.getAttribute("baseAction"));
		out.println("/css/tracker.css\" />");
		out.println();
		out.print("		<script type=\"text/javascript\" src=\"");
		out.print((String) request.getAttribute("baseAction"));
		out.println("/js/jquery-1.6.4.min.js\"></script>");
		out.print("		<script type=\"text/javascript\" src=\"");
		out.print((String) request.getAttribute("baseAction"));
		out.println("/js/jquery.wordWrap.js\"></script>");
		out.println("		<script>");
		out.print("				 $(document).ready( function() {");

		if (request.getPathInfo().equals("/config")) {
			out.print(
					"$('#filter').keyup(function() {var filterKey = this.value.toLowerCase();$('#loggers tbody tr').filter(function() {return $('select', this)[0].name.toLowerCase().indexOf(filterKey) == -1;}).hide();$('#loggers tbody tr').filter(function() {return $('select', this)[0].name.toLowerCase().indexOf(filterKey) != -1;}).show();});$('select').change(function() {$(this).parent().submit();});");
		}
		out.print("");
		if (request.getPathInfo().equals("/log")) {
			out.print(
					"$('#wrapCheck').click(function() {var textarea = $('#logText').get();if( ! $(this).attr('checked') ) {$(textarea).wordWrap('on');} else {$(textarea).wordWrap('off');}});var refresh = function() {$('#loading-mask').text('Loading...');$('#loading-mask').removeClass('error');$('#loading-mask').show();var data = {appender: $('#appender').val()};if($('#lines').val()!='') {data.lines = $('#lines').val();}$.ajax({url: '");
			out.print((String) request.getAttribute("baseAction"));
			out.print(
					"/taillog',data: data,dataType: 'text',cache: false,success: function(data) {if(data.indexOf('TrackerError')==-1) {$('#loading-mask').hide();$('#logText').val(data);$('#logText').get(0).scrollTop = $('#logText').get(0).scrollHeight;} else {$('#loading-mask').addClass('error');$('#loading-mask').text('Error: Check the log manually');}}});};$('#refresh').click(refresh);$('#lines').bind('keypress', function(e) { if(e.keyCode==13) { refresh(); return false; } });$('#download').click(function() {});");
			out.print(
					"var interval = null; $('#refreshCheck').click(function(){if($(this).attr('checked')) {if (interval == null){ interval = setInterval(function(){ refresh(); }, 5000)};} else {clearInterval(interval)}});");
		}
		out.println("});");
		out.println("		</script>");
		out.println(
				"		<!--[if !IE 7]><style type=\"text/css\">#wrap { display:table;height:100% }</style><![endif]-->");
		out.println("	</head>");
		out.println("	<body>");
		out.println("		<div id=\"wrap\">");
		out.println("			<div id=\"header\">");
		out.print("				<a href=\"");
		out.print((String) request.getAttribute("baseAction"));
		out.println("\">");
		out.print("					<img border=\"0\" alt=\"Anteros Log4j Web Tracker\" title=\"Anteros Log4j Web Tracker\" src=\"");
		out.print((String) request.getAttribute("baseAction"));
		out.println("/img/anteros_logo.png\" height=\"161\">");
		out.println("				</a>");
		out.println("			</div>");
		out.println("			<div id=\"navcontainer\">");
		out.println("				<ul id=\"navlist\">");
		out.print("					<li><a href=\"");
		out.print((String) request.getAttribute("baseAction"));
		out.print("/config\"");
		if (request.getPathInfo().equals("/config")) {
			out.print(" class=\"active\"");
		}
		out.println(">Configuração</a></li>");
		out.print("					<li><a href=\"");
		out.print((String) request.getAttribute("baseAction"));
		out.print("/log\"");
		if (request.getPathInfo().equals("/log")) {
			out.print(" class=\"active\"");
		}
		out.println(">Log</a></li>");
		out.println("				</ul>");
		out.println("			</div>");
		out.println("			<div class=\"clear\"></div>");
		out.println("			<div id=\"main\">");

		if (request.getPathInfo().equals("/config")) {
			List loggers = (List) request.getAttribute("loggers");

			out.println("				<div id=\"configuration\">");
			out.println("					<div id=\"filterContainer\">");
			out.println("						<div id=\"filterTextContainer\">");
			out.println("							<p>Filtro</p>");
			out.println("						</div>");
			out.println("						<div id=\"filterInputContainer\">");
			out.println("							<input type=\"text\" id=\"filter\" name=\"filter\" placeholder=\"Insira o nome do pacote ou parte dele\" spellcheck=\"false\"/>");
			out.println("						</div>");
			out.println("					</div>");
			out.println("					<div class=\"clear\"></div>");
			out.println("					<div id=\"loggersContainer\">");
			out.println("						<table id=\"loggers\">");
			out.println("							<thead>");
			out.println("								<tr>");
			out.println("									<th>Logger</th>");
			out.println("									<th>Level</th>");
			out.println("								</tr>");
			out.println("							</thead>");
			out.println("							<tbody>");

			for (int i = 0; i < loggers.size(); i++) {
				Logger logger = (Logger) loggers.get(i);

				out.print("								<tr class=\"logger-");
				out.print(i % 2 == 0 ? "pair" : "odd");
				out.println("\">");
				out.println("									<td class=\"logger-name\">");
				out.print("										<label for=\"");
				out.print(logger.getName());
				out.print("\">");
				out.print(logger.getName());
				out.println("</label>");
				out.println("									</td>");
				out.println("									<td class=\"logger-level\">");
				out.print("										<form action=\"");
				out.print((String) request.getAttribute("baseAction"));
				out.println("/config\" method=\"post\">");
				out.print("											<select class=\"select conf\" id=\"");
				out.print(logger.getName());
				out.print("\" name=\"");
				out.print(logger.getName());
				out.println("\">");
				out.print("												<option value=\"TRACE\"");

				if (logger.getEffectiveLevel().toString() == "TRACE") {
					out.print(" selected=\"selected\" ");
				}
				out.println(">TRACE</option>");
				out.print("												<option value=\"DEBUG\"");
				if (logger.getEffectiveLevel().toString() == "DEBUG") {
					out.print(" selected=\"selected\" ");
				}
				out.println(">DEBUG</option>");
				out.print("												<option value=\"INFO\"");
				if (logger.getEffectiveLevel().toString() == "INFO") {
					out.print(" selected=\"selected\" ");
				}
				out.println(">INFO</option>");
				out.print("												<option value=\"WARN\"");
				if (logger.getEffectiveLevel().toString() == "WARN") {
					out.print(" selected=\"selected\" ");
				}
				out.println(">WARN</option>");
				out.print("												<option value=\"ERROR\"");
				if (logger.getEffectiveLevel().toString() == "ERROR") {
					out.print(" selected=\"selected\" ");
				}
				out.println(">ERROR</option>");
				out.print("												<option value=\"FATAL\"");
				if (logger.getEffectiveLevel().toString() == "FATAL") {
					out.print(" selected=\"selected\" ");
				}
				out.println(">FATAL</option>");
				out.print("												<option value=\"OFF\"");
				if (logger.getEffectiveLevel().toString() == "OFF") {
					out.print(" selected=\"selected\" ");
				}
				out.println(">OFF</option>");
				// out.print(logger.getEffectiveLevel().toString());
				out.println("											</select>");
				out.println("										</form>");
				out.println("									</td>");
				out.println("								</tr>");
			}

			out.println("							</tbody>");
			out.println("						</table>");
			out.println("					</div>");
			out.println("				</div>");
		}
		out.print("");
		if (request.getPathInfo().equals("/log")) {
			out.println("				<div id=\"log\">");
			out.println("					<div id=\"options\">");
			out.println("						<div style=\"float: left;\">");
			out.println("							<div>");
			out.println("								<input id=\"wrapCheck\" type=\"checkbox\">");
			out.println("								<label id=\"wrapLabel\" for=\"wrapCheck\">Não quebrar a linha do log</label>");
			out.println("							</div>");
			out.println("							<div>");
			out.println("								<input id=\"refreshCheck\" type=\"checkbox\">");
			out.println("								<label id=\"refreshLabel\" for=\"refreshCheck\">Atualizar automático</label>");
			out.println("							</div>");
			out.println("						</div>");
			out.print("						<form action=\"");
			out.print((String) request.getAttribute("baseAction"));
			out.println("/getlog\">");
			out.println("							<div id=\"parent\" style=\"float: right;\">");
			out.println("								<div class=\"childs\">");
			out.println("									<div>");
			out.println("										<label class=\"label\" for=\"appender\">Arquivo</label>");
			out.println("									</div>");
			out.println("									<select class=\"select\" id=\"appender\" name=\"appender\">");

			List fileAppenders = (List) request.getAttribute("fileAppenders");
			for (int i = 0; i < fileAppenders.size(); i++) {
				FileAppender fap = (FileAppender) fileAppenders.get(i);

				out.print("										<option value=\"");
				out.print(fap.getName());
				out.print("\">");
				out.print(fap.getName());
				out.println("</option>");
			}
			out.println("									</select>");
			out.println("								</div>");
			out.println("								<div class=\"childs\">");
			out.println("									<div>");
			out.println("										<label class=\"label\" for=\"lines\">Qtde. linhas</label>");
			out.println("									</div>");
			out.println("									<input type=\"number\" id=\"lines\" name=\"lines\" value=\"20\" size=\"4\" style=\"margin-right: 5px;\" />");
			out.println("								</div>");
			out.println("								<div class=\"childs\">");
			out.println("									<button type=\"button\" class=\"btn\" id=\"refresh\">Atualizar</button>");
			out.println("									<button type=\"submit\" class=\"btn green\" id=\"download\">Download</button>");
			out.println("								</div>");
			out.println("							</div>");
			out.println("						</form>");
			out.println("						<div style=\"overflow: hidden;\">");
			out.println("							<div id=\"loading-mask\" style=\"display: none;\"></div>");
			out.println("						</div>");
			out.println("					</div>");
			out.println("					<textarea id=\"logText\" rows=\"20\" spellcheck=\"false\"></textarea>");
			out.println("				</div>");
		}
		out.println("			</div>");
		out.println("		</div>");
		out.println("		<div id=\"footer\">");
		out.println("			<div id=\"back-link\">");
		out.print("				<span><a href=\"");
		out.print(request.getContextPath());
		out.println("/\">&uarr; Ir para aplicação</a></span>");
		out.println("			</div>");
		out.println("			<div id=\"copyright\">");
		out.println("				<span>Copyright ");
		out.println("					<script>document.write(new Date().getFullYear())</script>");
		out.println("					&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;<i>Distribuído por &nbsp;</i>");
		out.println("					<a href=\"http://www.anteros.com.br\" target=\"_blank\">Anteros Tecnologia</a>");
		out.println("				</span>");
		out.println("			</div>");
		out.println("		</div>");
		out.println("	</body>");
		out.println("</html>");
	}

	synchronized private byte[] getJQueryMin() throws IOException {
		if (jqueryMin == null) {
			InputStream in = this.getClass().getResourceAsStream("js/jquery-1.6.4.min.js");
			jqueryMin = toByteArray(in);
			in.close();
		}
		return jqueryMin;
	}

	synchronized private byte[] getJQueryWordWrap() throws IOException {
		if (jqueryWordWrap == null) {
			InputStream in = this.getClass().getResourceAsStream("js/jquery.wordWrap.js");
			jqueryWordWrap = toByteArray(in);
			in.close();
		}
		return jqueryWordWrap;
	}

	synchronized private byte[] getCSS() throws IOException {
		if (css == null) {
			InputStream in = this.getClass().getResourceAsStream("css/tracker.css");
			css = toByteArray(in);
			in.close();
		}
		return css;
	}

	synchronized private byte[] getLogo() throws IOException {
		if (logo == null) {
			InputStream in = this.getClass().getResourceAsStream("img/anteros_logo.png");
			logo = toByteArray(in);
			in.close();
		}
		return logo;
	}

	synchronized private byte[] getFavicon() throws IOException {
		if (favicon == null) {
			InputStream in = this.getClass().getResourceAsStream("img/favicon.ico");
			favicon = toByteArray(in);
			in.close();
		}
		return favicon;
	}

	/**
	 * Get the contents of an <code>InputStream</code> as a <code>byte[]</code>.
	 * <p>
	 * This method buffers the input internally, so there is no need to use a
	 * <code>BufferedInputStream</code>.
	 * 
	 * @param input
	 *            the <code>InputStream</code> to read from
	 * @return the requested byte array
	 * @throws NullPointerException
	 *             if the input is null
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	private byte[] toByteArray(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		StreamUtils.readStream(input, output, BUFFER_SIZE);
		return output.toByteArray();
	}
}
