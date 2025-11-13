package be.nabu.eai.module.services.vm;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.ParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.utils.io.IOUtils;

public class XMLPrettifier {
	
	@SuppressWarnings("resource")
	public static void main(String...args) throws IOException, ParseException {
		boolean standalone = false;
		File file = new File("/home/alex/nabu/repositories/main/nabu/cms/core/providers/security/passwordAuthenticator/service.xml");
		if (standalone) {
			XMLPrettifier xmlPrettifier = new XMLPrettifier();
			try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
				xmlPrettifier.prettify(inputStream);
			}
		}
		else {
			try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
				Sequence sequence = VMServiceManager.parseSequence(IOUtils.wrap(inputStream));
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				VMServiceManager.formatSequence(IOUtils.wrap(output), sequence, true);
				System.out.println(new String(output.toByteArray()));
				VMServiceManager.parseSequence(IOUtils.wrap(new ByteArrayInputStream(output.toByteArray())));
			}
		}
	}
	public static Document toDocument(InputStream xml, boolean namespaceAware) throws SAXException, IOException, ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// no DTD
		factory.setValidating(false);
		factory.setNamespaceAware(namespaceAware);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		// allow no external access, as defined http://docs.oracle.com/javase/7/docs/api/javax/xml/XMLConstants.html#FEATURE_SECURE_PROCESSING an empty string means no protocols are allowed
		try {
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		}
		catch (Exception e) {
			// not supported in later versions..............
		}
		return factory.newDocumentBuilder().parse(xml);
	}
	public static String toString(Node node, boolean omitXMLDeclaration, boolean prettyPrint) throws TransformerException {
        StringWriter string = new StringWriter();
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();
        if (omitXMLDeclaration) {
        	transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        }
        if (prettyPrint) {
        	transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        }
        transformer.transform(new DOMSource(node), new StreamResult(string));
        return string.toString();
	}
	public void prettify(InputStream input) {
		try {
			Document document = toDocument(input, false);
			prettify(document.getDocumentElement());
			System.out.println(toString(document.getDocumentElement(), true, true));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public void prettify(Element element) {
		// refactor the steps
		if (element.getNodeName().equals("steps")) {
			String type = element.getAttribute("xsi:type");
			String targetType = null;
			if (type != null) {
				targetType = type.replaceAll("^.*\\.([^.]+)$", "$1");
				targetType = targetType.substring(0, 1).toLowerCase() + targetType.substring(1);
			}
			if (targetType != null) {
				element.getOwnerDocument().renameNode(element, null, targetType);
				element.removeAttribute("xsi:type");
			}
			else {
				System.out.println("Unknown type: " + type);
			}
			// remove some general attributes
			removeIf(element, "disabled", "false");
			removeIf(element, "recache", "false");
			removeIf(element, "asynchronous", "false");
			removeIf(element, "fixedValue", "false");
			removeIf(element, "mask", "false");
			removeIf(element, "optional", "false");
			element.removeAttribute("lineNumber");
		}
		NodeList childNodes = element.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node item = childNodes.item(i);
			if (item instanceof Element) {
				prettify((Element) item);
			}
		}
	}
	
	private void removeIf(Element element, String attribute, String defaultValue) {
		String value = element.getAttribute(attribute);
		if (value != null && value.equalsIgnoreCase(defaultValue)) {
			element.removeAttribute(attribute);
		}
	}
}
