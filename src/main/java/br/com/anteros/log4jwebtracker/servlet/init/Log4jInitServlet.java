package br.com.anteros.log4jwebtracker.servlet.init;

import java.io.File;
import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.Loader;
import org.apache.log4j.xml.DOMConfigurator;

/**
 * Log4J servlet initializer.
 *
 * @author Mariano Ruiz
 * @author Eduardo Albertini
 */
public class Log4jInitServlet extends HttpServlet {

	private static final String LOG4J_CONFIG_LOCATION = "log4jConfigLocation";
	private static final long serialVersionUID = 1L;

	public void init() throws ServletException {
		String prefix = getServletContext().getRealPath("/");
		String file = getInitParameter(LOG4J_CONFIG_LOCATION);

		if (file == null)
			file = getServletContext().getInitParameter(LOG4J_CONFIG_LOCATION);

		if (file == null) {
			URL url = Loader.getResource("log4j.properties");
			if (new File(url.getFile()).exists()) {
				PropertyConfigurator.configure(url);
			} else {
				url = Loader.getResource("log4j.xml");
				if (new File(url.getFile()).exists()) {
					DOMConfigurator.configure(url);
				} else {
					System.err.println("*** Log4j configuration file not found. ***");
				}
			}
		} else {
			String path = (prefix + file).replaceAll("\\\\", "/");
			if (new File(path).exists()) {
				if (path.indexOf(".xml") != -1) {
					DOMConfigurator.configure(path);
				} else if (path.indexOf(".properties") != -1) {
					PropertyConfigurator.configure(path);
				} else {
					System.err.println("*** Log4j configuration file format unknown. ***");
				}
			} else {
				System.err.println(
						"*** Log4j configuration file " + path + " not found. ***");
			}
		}
		super.init();
	}
}
