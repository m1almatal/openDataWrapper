package openDataWrapper.general;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;

/**
 * 
 * @author alexis.linard
 * 
 */
public class XSLConstructor {
	static Logger logger = Logger.getLogger(XSLConstructor.class);

	public String XSLFile;
	public Document XMLFile;
	public Properties properties;
	public String dataset;
	public String speMappingPath;
	public String url;
	public String titre;
	public String publisher;

	public final String URIBase = "http://lodpaddle.univ-nantes.fr/";

	private final String intVide = "-120404040";
	private final String decVide = "-120404040.00";
	private final String stringVide = "undefined";
	private Properties speProp;

	/**
	 * 
	 * @param xSLFile_
	 *            the path to the xslt file
	 * @param document
	 *            the XML jdom2 document tree
	 * @param p
	 *            mapping;properties properties
	 * @param Dataset
	 *            the name of the current dataset
	 * @param spePath
	 *            the path to the specific property file
	 */
	public XSLConstructor(String xSLFile_, Document document, Properties p,
			String Dataset, String spePath, String url_, String titre_,
			String publisher_) {
		XSLFile = xSLFile_;
		XMLFile = document;
		properties = p;
		dataset = Dataset;
		speMappingPath = spePath;
		url = url_;
		titre = titre_;
		publisher = publisher_;
		// logger.error(XSLFile);
	}

	/**
	 * This function construct automatically the XSLT file. It look at the first
	 * element of the xml, and deduct the form of the answer. The foaf:name
	 * resolved xml tag is put first, as it plays the subject role. The function
	 * checks if a specific property file is given, and if yes, uses it in
	 * priority. Each property type have is own XSLT out.
	 * 
	 * @param mappingPath
	 *            the path to the file that contains the mapping between the XML
	 *            tag and the vocabulary
	 * @return true if the construction is ok, false else.
	 */
	public boolean construct(String mappingPath) {
		Document document;
		// chargement du XML
		try {
			document = XMLFile;
			Map<String, MappingUnit> map = new HashMap<String, MappingUnit>();
			Element element = document.getRootElement().getChild("data")
					.getChild("element");
			// on possède ainsi un élément du fichier
			List<Element> listeTag = element.getChildren();
			Iterator<Element> it = listeTag.iterator();
			File f = new File(speMappingPath);
			if (f.exists()) {
				logger.info("Personnal property file detected");
				speProp = new Properties();
				speProp.load(new FileReader(f));
			}
			System.out.print("XSL construction preprocessing");
			boolean modification = false;
			while (it.hasNext()) {
				System.out.print(".");
				Element courant = it.next();

				// patch pour geo/name => a corriger
				String name = courant.getName();

				if (courant.getChildren().size() == 1) {
					name += "/name";
				}
				if (speProp != null && speProp.containsKey(name)) {
					map.put(name, new MappingUnit((String) speProp.get(name)));
				} else {
					if (properties.containsKey(name)) {

						// Vérification de la source et ajout de la nouvelle
						// source si besoin
						String[] liste = ((String) properties.get(name))
								.split(",");
						boolean gotSource = false;
						for (String s : liste) {
							if (s.equals(dataset)) {
								gotSource = true;
							}
						}
						if (!gotSource) {
							properties.setProperty(name,
									((String) properties.get(name)) + ","
											+ dataset);
							modification = true;
						}

						map.put(name,
								new MappingUnit((String) properties.get(name)));
					} else {
						// la propriété n'existe pas, il faut la créer
						properties.setProperty(name, "TEMPORAIRE:" + name
								+ ",string," + dataset);
						System.err.println("WARNING: new property added: "
								+ name);
						modification = true;
						map.put(name,
								new MappingUnit((String) properties.get(name)));
					}
				}
			}
			if (modification) {
				properties
						.store(new FileOutputStream(mappingPath, false),
								"mapping file! If 'TEMPORAIRE:XXX' appears somewhere, "
										+ "that means that open data wrapper have generated it because "
										+ "it hasn't found the right mapping. please check it out!");

			}
			// here, everything is identified in map
			System.out.println("done!");
			return generateXSL(map);

		} catch (IOException e) {
			System.err.println("Unable to open/write into the mapping file "
					+ mappingPath + " " + e.getMessage());
			return false;
		}
	}

	private boolean generateXSL(Map<String, MappingUnit> map) {
		try {
			// Create file
			System.out.println("XSL construction processing...");
			// logger.error(XSLFile);
			FileWriter fstream = new FileWriter(XSLFile);
			BufferedWriter out = new BufferedWriter(fstream);

			// ready to write

			out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
					+ "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n"
					+ "	xmlns:ex=\"http://www.example.org/\" \n"
					+ "	xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n"
					+ "	xmlns:foaf=\"http://xmlns.com/foaf/0.1/\"\n"
					+ "	> \n"
					+ "<xsl:output method='text' encoding='UTF-8'/>\n"
					+ "\n"
					+ "<xsl:template match=\"document\">\n"
					+ "	<xsl:apply-templates select=\"data\"/>\n"
					+ "</xsl:template>\n"
					+ "\n"
					+ "<xsl:variable name=\"incr\" select=\"0\"></xsl:variable>\n"
					+ "\n" + "<xsl:template match=\"data\">\n"
					+ "	<xsl:text>\n");

			prefixes(out);

			vo_id(out);

			out.write("\n" + "</xsl:text>\n" + "	<xsl:apply-templates/>\n"
					+ "</xsl:template>\n" + "\n"
					+ "<xsl:template match=\"element\">\n");

			if (templateDef(map, out)) {

				out.write("\n</xsl:template>\n\n");

				templateWrite(map, out);

				out.write("</xsl:stylesheet>");
			} else {
				return false;
			}

			// Close the output stream
			System.out.println("Processing done!");
			out.close();
			return true;
		} catch (Exception e) {// Catch exception if any
			e.getStackTrace();
			System.err
					.println("Error. please Check that your dataset have a foaf:name property! "
							+ e.getMessage());
			return false;
		}

	}

	/**
	 * This function manage VoID information about the dataset
	 * 
	 * @param out
	 * @throws IOException
	 */
	private void vo_id(BufferedWriter out) throws IOException {

		// logger.info("beginning of VoID");

		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date date = new Date();
		String d = dateFormat.format(date);

		out.write("\n&lt;" + URIBase + dataset
				+ "&gt; rdf:type void:Dataset ;\n" + "\tfoaf:homepage \"" + url
				+ "\";\n" + "\tdcterms:title \"" + titre + "\"^^xsd:string ;\n"
				+ "\tdcterms:description \"" + titre + "\"^^xsd:string ;\n"
				+ "\tdcterms:created \"" + d + "\"^^xsd:date;\n"
				+ "\tdcterms:publisher :pub .\n" + "\n:pub rdfs:label \""
				+ publisher + "\".\n");

	}

	private String templateName(String courant, Iterator<String> it,
			Map<String, MappingUnit> map) {
		String s = new String(
				"<xsl:template match=\""
						+ courant
						+ "\">"
						+ "<xsl:choose>"
						+ "<xsl:when test=\". = 'null'\"></xsl:when>\n"
						+ "<xsl:otherwise>"
						+ "<xsl:value-of select=\"concat(concat('&lt;"
						+ URIBase
						+ dataset
						+ "/',encode-for-uri(.)),'&gt;')\"/>&#009; "
						// + "<xsl:value-of select=\"concat(concat('&lt;"
						// + URIBase
						// + dataset
						// +
						// "/',translate(translate(translate(translate(translate(translate(translate(./text(),'&quot;',' '),'«',' '),'&gt;',' '),'&lt;',' '),'  ',' '),' ','_'),'.','_')),'&gt;')\"/>&#009; "
						+ map.get(courant).vocabulaire
						+ " &#009; \"<xsl:value-of select=\"translate(., '&quot;','')\"/>\"^^xsd:string ;"
						+ lastRetour(it)
						+ "</xsl:otherwise></xsl:choose></xsl:template>\n\n");
		return s;
	}

	private String templateInt(String courant, Iterator<String> it,
			Map<String, MappingUnit> map) {
		String s = new String("<xsl:template match=\"" + courant + "\">"
				+ "<xsl:choose>" + "<xsl:when test=\". = 'null'\">" + "&#009;"
				+ map.get(courant).vocabulaire + "&#009; \"" + intVide
				+ "\"^^xsd:int " + last(it) + lastRetour(it) + "</xsl:when>\n"
				+ "<xsl:otherwise>" + "&#009;" + map.get(courant).vocabulaire
				+ "&#009; \"<xsl:value-of select=\".\"/>\"^^xsd:int "
				+ last(it) + lastRetour(it)
				+ "</xsl:otherwise></xsl:choose></xsl:template>\n\n");
		return s;
	}

	private String templateFloat(String courant, Iterator<String> it,
			Map<String, MappingUnit> map) {
		String s = new String("<xsl:template match=\"" + courant + "\">"
				+ "<xsl:choose>" + "<xsl:when test=\". = 'null'\">" + "&#009;"
				+ map.get(courant).vocabulaire + "&#009; \"" + decVide
				+ "\"^^xsd:decimal " + last(it) + lastRetour(it)
				+ "</xsl:when>\n" + "<xsl:otherwise>" + "&#009;"
				+ map.get(courant).vocabulaire
				+ "&#009; \"<xsl:value-of select=\".\"/>\"^^xsd:decimal "
				+ last(it) + lastRetour(it)
				+ "</xsl:otherwise></xsl:choose></xsl:template>\n\n");
		return s;
	}

	private String templateString(String courant, Iterator<String> it,
			Map<String, MappingUnit> map) {
		String s = new String(
				"<xsl:template match=\""
						+ courant
						+ "\">\n"
						+ "<xsl:choose>"
						+ "<xsl:when test=\". = 'null'\">"
						+ "&#009;"
						+ map.get(courant).vocabulaire
						+ "&#009; \""
						+ stringVide
						+ "\"^^xsd:string "
						+ last(it)
						+ lastRetour(it)
						+ "</xsl:when>\n"
						+ "<xsl:otherwise>"
						+ "&#009;"
						+ map.get(courant).vocabulaire
						+ "&#009; \""
						+ "<xsl:value-of select=\"translate(., '&quot;','')\"/>\"^^xsd:string "
						+ last(it) + lastRetour(it)
						+ "</xsl:otherwise></xsl:choose></xsl:template>\n\n");
		return s;
	}

	private String templateCoord(String courant, Iterator<String> it,
			Map<String, MappingUnit> map) {
		String s = new String(
				"<xsl:template match=\""
						+ courant
						+ "\">"
						+ "&#009;"
						+ "geo:lat"
						+ "&#009;\""
						+ "<xsl:value-of select=\"substring-after(substring-before(.,' ,'),'[ ')\"/>\"^^xsd:decimal ;\n"
						+ "&#009;"
						+ "geo:long"
						+ "&#009;\""
						+ "<xsl:value-of select=\"substring-before(substring-after(.,', '),']')\"/>\"^^xsd:decimal  "
						+ last(it) + lastRetour(it) + "</xsl:template>\n\n");
		return s;
	}

	private String templateEmpty(String courant, Iterator<String> it,
			Map<String, MappingUnit> map) {
		logger.info("La propriété " + courant + " va être ignorée!");
		String s = new String();
		if (it.hasNext()) {
			s += new String("<xsl:template match=\"" + courant
					+ "\"></xsl:template>\n\n");
		} else {
			s += new String("<xsl:template match=\"" + courant
					+ "\"><xsl:text>&#009;.\n\n</xsl:text></xsl:template>\n\n");
		}
		return s;
	}

	private void templateWrite(Map<String, MappingUnit> map, BufferedWriter out)
			throws IOException {
		Set<String> keys = map.keySet();
		Iterator<String> it = keys.iterator();
		it = keys.iterator();
		while (it.hasNext()) {
			String courant = it.next();
			if (!map.get(courant).ignore) {
				if (map.get(courant).vocabulaire.equals("foaf:name")) {
					out.write(templateName(courant, it, map));
				} else {
					if (courant == "_l") {
						out.write(templateCoord(courant, it, map));

					} else {
						if (map.get(courant).type.equals("decimal")) {
							out.write(templateFloat(courant, it, map));

						} else {
							if (map.get(courant).type.equals("integer")) {
								out.write(templateInt(courant, it, map));

							} else {
								// on suppose que le cas général est string
								out.write(templateString(courant, it, map));
							}
						}
					}
				}
			} else {
				out.write(templateEmpty(courant, it, map));
			}
		}
	}

	private String templateType() {
		String s = new String("<xsl:text>&#009;rdf:type&#009; pdll:" + dataset
				+ ";\n</xsl:text>\n");
		return s;
	}

	private String lastRetour(Iterator<String> it) {
		if (it.hasNext())
			return "\n";
		else
			return "\n\n";
	}

	private String last(Iterator<String> it) {
		if (it.hasNext())
			return ";";
		else
			return ".";
	}

	// private boolean lastItem(Iterator<String> it){
	// Iterator<String> i = it;
	// if (i.hasNext()){
	// if(i.next().split(regex))
	// return lastItem(i);
	// }
	// else
	// return true;
	// }

	private boolean templateDef(Map<String, MappingUnit> map, BufferedWriter out)
			throws IOException {
		Set<String> keys = map.keySet();
		Iterator<String> it = keys.iterator();
		boolean trouve = false;
		String principal = "";
		while (it.hasNext() && !trouve) {
			String courant = it.next();
			if (!map.get(courant).ignore
					&& map.get(courant).vocabulaire.equals("foaf:name")) {
				out.write("\t<xsl:apply-templates select=\"" + courant
						+ "\"/>\n");
				// cas particulier du type, placé directement après le foaf:name
				out.write(templateType());
				trouve = true;
				principal = courant;
			}
		}
		if (!trouve) {
			System.err
					.println("id not found! We absolutely need some properties that will give a foaf:name !");
			return false;
		}
		it = keys.iterator();
		while (it.hasNext()) {
			String courant = it.next();
			if (!courant.equals(principal)) {
				out.write("\t<xsl:apply-templates select=\"" + courant
						+ "\"/>\n");
			}
		}
		return true;
	}

	private void prefixes(BufferedWriter out) throws IOException {
		// logger.info("prefixes management");
		String prefixChaine = (String) properties.get("$$prefixes$$");
		String[] prefixes = prefixChaine.split(",");
		for (String value : prefixes) {
			out.write("@prefix "
					+ value.replace("<", "&lt;").replace(">", "&gt;") + " .\n");
		}
	}
}
