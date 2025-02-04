/*
 * XmlElementWrapperPluginTest.java
 * 
 * Copyright (C) 2009, Tobias Warneke
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.sun.tools.xjc.addon.xew;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import javax.tools.*;
import javax.xml.XMLConstants;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.validation.SchemaFactory;

import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Driver;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.reader.Const;
import com.sun.tools.xjc.reader.internalizer.DOMForest;
import com.sun.tools.xjc.reader.xmlschema.parser.XMLSchemaInternalizationLogic;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Testcases for the XEW Plugin.
 * 
 * @author Tobias Warneke
 */
@Ignore("Struggles with newer JVMs")
public class XmlElementWrapperPluginTest {

	private static final String	PREGENERATED_SOURCES_PREFIX	= "src/test/generated_resources/";
	private static final String	GENERATED_SOURCES_PREFIX	= "target/test/generated_xsd_classes/";

	private static final Log	logger						= LogFactory.getLog(XmlElementWrapperPluginTest.class);

	@Test
	public void testUsage() throws Exception {
		assertNotNull(new XmlElementWrapperPlugin().getUsage());
	}

	@Test(expected = BadCommandLineException.class)
	public void testUnknownOption() throws Exception {
		runTest("different-namespaces", new String[] { "-Xxew:unknown" }, false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidInstantiationMode() throws Exception {
		runTest("element-list-extended", new String[] { "-Xxew:instantiate invalid" }, false);
	}

	@Test(expected = BadCommandLineException.class)
	public void testInvalidControlFile() throws Exception {
		runTest("element-list-extended", new String[] { "-Xxew:control invalid" }, false);
	}

	@Test(expected = BadCommandLineException.class)
	public void testInvalidCollectionClass() throws Exception {
		runTest("element-list-extended", new String[] { "-Xxew:collection badvalue" }, false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidCustomization() throws Exception {
		runTest("element-with-invalid-customization", null, false);
	}

	/**
	 * This test works reliably on Java7 but produces different results from run to run on Java8.
	 */
	@Test
	public void testDifferentNamespacesForWrapperAndElement() throws Exception {
		// Plural form in this case will have no impact as all properties are already in plural:
		runTest("different-namespaces", new String[] { "-Xxew:collection", "java.util.LinkedList", "-Xxew:instantiate",
		        "lazy", "-Xxew:plural" }, false, "BaseContainer", "Container", "Entry", "package-info");
	}

	@Test
	public void testInnerElement() throws Exception {
		runTest("inner-element",
		            new String[] { "-verbose", "-Xxew:instantiate none",
		                    "-Xxew:control " + getClass().getResource("inner-element-control.txt").getFile() },
		            true, "Filesystem", "Volumes", "package-info");
	}

	@Test
	public void testInnerElementWithValueObjects() throws Exception {
		runTest("inner-element-value-objects", new String[] { "-debug" }, false, "Article", "Articles",
		            "ArticlesCollections", "Filesystem", "Publisher", "Volume", "package-info", "impl.ArticleImpl",
		            "impl.ArticlesImpl", "impl.ArticlesCollectionsImpl", "impl.FilesystemImpl", "impl.PublisherImpl",
		            "impl.VolumeImpl", "impl.ObjectFactory", "impl.JAXBContextFactory", "impl.package-info");
	}

	@Test
	public void testAnnotationReference() throws Exception {
		// "Markup.java" cannot be verified for content because the content is changing from
		// one compilation to other as order of @XmlElementRef/@XmlElement annotations is not pre-defined
		// (set is used as their container).
		runTest("annotation-reference", new String[] { "-verbose", "-debug" }, false, "ClassCommon", "ClassesEu",
		            "ClassesUs", "ClassExt", "Markup", "Para", "SearchEu", "SearchMulti", "package-info");
	}

	@Test
	public void testElementAsParametrisationPublisher() throws Exception {
		runTest("element-as-parametrisation-publisher",
		            new String[] { "-debug",
		                    "-Xxew:control " + getClass()
		                                .getResource("element-as-parametrisation-publisher-control.txt").getFile() },
		            false, "Article", "Articles", "ArticlesCollections", "Publisher", "package-info");
	}

	@Test
	public void testElementAsParametrisationFamily() throws Exception {
		runTest("element-as-parametrisation-family",
		            new String[] { "-debug",
		                    "-Xxew:control " + getClass().getResource("element-as-parametrisation-family-control.txt")
		                                .getFile(),
		                    "-Xxew:summary " + GENERATED_SOURCES_PREFIX + "summary.txt" },
		            false, "Family", "FamilyMember", "package-info");

		String summaryFile = FileUtils.readFileToString(new File(GENERATED_SOURCES_PREFIX + "summary.txt"));

		assertTrue(summaryFile.contains("1 candidate(s) being considered"));
		assertTrue(summaryFile.contains("0 modification(s) to original code"));
		assertTrue(summaryFile.contains("0 deletion(s) from original code"));
	}

	@Test
	public void testElementWithParent() throws Exception {
		runTest("element-with-parent", new String[] { "-debug" }, false, "Alliance", "Group", "Organization",
		            "package-info");
	}

	@Test
	public void testElementAny() throws Exception {
		runTest("element-any", new String[] { "-quiet", "-Xxew:plural" }, false, "Message", "package-info");
	}

	@Test
	public void testElementAnyType() throws Exception {
		runTest("element-any-type", new String[] { "-Xxew:plural" }, false, "Conversion", "Entry", "package-info");
	}

	@Test
	public void testElementMixed() throws Exception {
		// Most classes cannot be tested for content
		runTest("element-mixed", new String[] { "-debug" }, false, "B", "Br", "I", "AnyText", "package-info");
	}

	@Test
	public void testElementListExtended() throws Exception {
		// This run is configured from XSD (<xew:xew ... >):
		runTest("element-list-extended", null, false, "Foo", "package-info");
	}

	@Test
	public void testElementNameCollision() throws Exception {
		// Most classes cannot be tested for content
		runTest("element-name-collision", new String[] { "-debug", "-Xxew:instantiate", "lazy" }, false, "Root",
		            "package-info");
	}

	@Test
	public void testElementScoped() throws Exception {
		// Most classes cannot be tested for content
		runTest("element-scoped", new String[] { "-debug" }, false, "Return", "SearchParameters", "package-info");
	}

	@Test
	public void testElementWithAdapter() throws Exception {
		// Plural form in this case will have no impact as there is property customization:
		runTest("element-with-adapter",
		            new String[] { "-Xxew:plural", "-Xxew:collectionInterface java.util.Collection" }, false,
		            "Calendar", "Adapter1", "package-info");
	}

	@Test
	public void testElementWithCustomization() throws Exception {
		// This run is additionally configured from XSD (<xew:xew ... >):
		runTest("element-with-customization", new String[] { "-debug", "-Xxew:plural" }, false, "PostOffice", "Args",
		            "package-info");
	}

	@Test
	public void testElementReservedWord() throws Exception {
		runTest("element-reserved-word", null, false, "Class", "Method", "package-info");
	}

	@Test
	public void testSubstitutionGroups() throws Exception {
		runTest("substitution-groups", null, false, "Address", "ContactInfo", "Customer", "PhoneNumber",
		            "package-info");
	}

	@Test
	public void testUnqualifiedSchema() throws Exception {
		runTest("unqualified", null, false, "RootElement", "package-info");
	}

	/**
	 * Standard test for XSD examples.
	 * 
	 * @param testName
	 *            the prototype of XSD file name / package name
	 * @param extraXewOptions
	 *            to be passed to plugin
	 * @param generateEpisode
	 *            generate episode file and check the list of classes included into it
	 * @param classesToCheck
	 *            expected classes/files in target directory; these files content is checked if it is present in
	 *            resources directory; {@code ObjectFactory.java} is automatically included
	 */
	static void runTest(String testName, String[] extraXewOptions, boolean generateEpisode, String... classesToCheck)
	            throws Exception {
		String resourceXsd = testName + ".xsd";
		String packageName = testName.replace('-', '_');

		// Force plugin to reinitialize the logger:
		System.clearProperty(XmlElementWrapperPlugin.COMMONS_LOGGING_LOG_LEVEL_PROPERTY_KEY);

		URL xsdUrl = XmlElementWrapperPluginTest.class.getResource(resourceXsd);

		File targetDir = new File(GENERATED_SOURCES_PREFIX);

		targetDir.mkdirs();

		PrintStream loggingPrintStream = new PrintStream(
		            new LoggingOutputStream(logger, LoggingOutputStream.LogLevel.INFO, "[XJC] "));

		String[] opts = ArrayUtils.addAll(extraXewOptions, "-no-header", "-extension", "-Xxew", "-d",
		            targetDir.getPath(), xsdUrl.getFile());

		String episodeFile = new File(targetDir, "episode.xml").getPath();

		// Episode plugin should be triggered after Xew, see https://github.com/dmak/jaxb-xew-plugin/issues/6
		if (generateEpisode) {
			opts = ArrayUtils.addAll(opts, "-episode", episodeFile);
		}

		assertTrue("XJC compilation failed. Checked console for more info.",
		            Driver.run(opts, loggingPrintStream, loggingPrintStream) == 0);

		if (generateEpisode) {
			// FIXME: Episode file actually contains only value objects
			Set<String> classReferences = getClassReferencesFromEpisodeFile(episodeFile);

			if (Arrays.asList(classesToCheck).contains("package-info")) {
				classReferences.add(packageName + ".package-info");
			}

			assertEquals("Wrong number of classes in episode file", classesToCheck.length, classReferences.size());

			for (String className : classesToCheck) {
				assertTrue(className + " class is missing in episode file;",
				            classReferences.contains(packageName + "." + className));
			}
		}

		targetDir = new File(targetDir, packageName);

		Collection<String> generatedJavaSources = new HashSet<String>();

		// *.properties files are ignored:
		for (File targetFile : FileUtils.listFiles(targetDir, new String[] { "java" }, true)) {
			// This is effectively the path of targetFile relative to targetDir:
			generatedJavaSources
			            .add(targetFile.getPath().substring(targetDir.getPath().length() + 1).replace('\\', '/'));
		}

		// This class is added and checked by default:
		classesToCheck = ArrayUtils.add(classesToCheck, "ObjectFactory");

		assertEquals("Wrong number of generated classes " + generatedJavaSources + ";", classesToCheck.length,
		            generatedJavaSources.size());

		for (String className : classesToCheck) {
			className = className.replace('.', '/') + ".java";

			assertTrue(className + " is missing in target directory", generatedJavaSources.contains(className));
		}

		// Check the contents for those files which exist in resources:
		for (String className : classesToCheck) {
			className = className.replace('.', '/');

			AssertionError lastFailedAssertion = null;

			byte sourceFileSuffix = -1;

			while (true) {
				sourceFileSuffix++;

				File sourceFile = new File(PREGENERATED_SOURCES_PREFIX + packageName,
				            className + (sourceFileSuffix == 0 ? "" : "_" + sourceFileSuffix) + ".java");

				if (!sourceFile.isFile()) {
					if (lastFailedAssertion != null) {
						throw lastFailedAssertion;
					}

					break;
				}

				String targetClassName = className + ".java";

				try {
					// To avoid CR/LF conflicts:
					assertEquals("For " + targetClassName + " in " + PREGENERATED_SOURCES_PREFIX + packageName,
					            FileUtils.readFileToString(sourceFile, StandardCharsets.UTF_8).replace("\r", ""),
					            FileUtils.readFileToString(new File(targetDir, targetClassName), StandardCharsets.UTF_8)
					                        .replace("\r", ""));

					break;
				}
				catch (AssertionError e) {
					lastFailedAssertion = e;
				}
			}
		}

		JAXBContext jaxbContext = compileAndLoad(packageName, targetDir, generatedJavaSources);

		URL xmlTestFile = XmlElementWrapperPluginTest.class.getResource(testName + ".xml");

		if (xmlTestFile != null) {
			StringWriter writer = new StringWriter();

			SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			unmarshaller.setSchema(schemaFactory.newSchema(xsdUrl));

			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			Object bean = unmarshaller.unmarshal(xmlTestFile);
			marshaller.marshal(bean, writer);

			XMLUnit.setIgnoreComments(true);
			XMLUnit.setIgnoreWhitespace(true);
			Diff xmlDiff = new Diff(IOUtils.toString(xmlTestFile, StandardCharsets.UTF_8), writer.toString());

			assertXMLEqual("Generated XML is wrong: " + writer.toString(), xmlDiff, true);
		}
	}

	/**
	 * The method performs:
	 * <ul>
	 * <li>Compilation of given set of Java source files
	 * <li>Construction of custom class loader
	 * <li>Creation of JAXB context
	 * </ul>
	 * 
	 * @param packageName
	 *            package name to which java classes belong to
	 * @param targetDir
	 *            the target directory
	 * @param generatedJavaSources
	 *            list of Java source files which should become a part of JAXB context
	 */
	private static JAXBContext compileAndLoad(String packageName, File targetDir,
	            Collection<String> generatedJavaSources) throws MalformedURLException, JAXBException {
		JavaFileObject[] javaSources = new JavaFileObject[generatedJavaSources.size()];

		int i = 0;
		for (String javaSource : generatedJavaSources) {
			File file = new File(targetDir, javaSource);
			javaSources[i++] = new SimpleJavaFileObject(file.toURI(), JavaFileObject.Kind.SOURCE) {
				@Override
				public InputStream openInputStream() throws IOException {
					return Files.newInputStream(file.toPath());
				}
			};
		}

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

		Map<String, InMemoryJavaFileObject> targets = new HashMap<>();
		try (JavaFileManager manager = new CapturingFileManager(
				compiler.getStandardFileManager(null, null, null),
				targets
		)) {
			if (!compiler.getTask(null, manager, null, null, null, Arrays.asList(javaSources)).call()) {
				fail("javac failed");
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		ClassLoader classLoader = new InMemoryClassLoader(targets);

		return JAXBContext.newInstance(packageName, classLoader);
	}

	/**
	 * Return values of all {@code <jaxb:class ref="..." />} attributes.
	 */
	private static Set<String> getClassReferencesFromEpisodeFile(String episodeFile) throws SAXException {
		DOMForest forest = new DOMForest(new XMLSchemaInternalizationLogic(), new Options());

		Document episodeDocument = forest.parse(new InputSource(episodeFile), true);

		NodeList nodeList = episodeDocument.getElementsByTagNameNS(Const.JAXB_NSURI, "class");
		Set<String> classReferences = new HashSet<String>();

		for (int i = 0, len = nodeList.getLength(); i < len; i++) {
			classReferences.add(((Element) nodeList.item(i)).getAttribute("ref"));
		}

		return classReferences;
	}

	private static class InMemoryJavaFileObject extends SimpleJavaFileObject {

		private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		private InMemoryJavaFileObject(String className) throws URISyntaxException {
			super(new URI(null, null, className, null), Kind.CLASS);
		}

		@Override
		public String getName() { return uri.getRawSchemeSpecificPart(); }

		@Override
		public OutputStream openOutputStream() {
			return outputStream;
		}

		byte[] toByteArray() {
			return outputStream.toByteArray();
		}
	}

	private static class CapturingFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

		private final Map<String, InMemoryJavaFileObject> targets;

		private CapturingFileManager(StandardJavaFileManager target, Map<String, InMemoryJavaFileObject> targets) {
			super(target);
			this.targets = targets;
		}

		@Override
		public JavaFileObject getJavaFileForOutput(
				Location location, String className,
				JavaFileObject.Kind kind, FileObject sibling
		) {
			InMemoryJavaFileObject target;
			try {
				target = new InMemoryJavaFileObject(className);
			} catch (URISyntaxException e) {
				throw new AssertionError(e);
			}
			targets.put(className, target);
			return target;
		}
	}
	static class InMemoryClassLoader extends ClassLoader {

		private final Map<String, InMemoryJavaFileObject> targets;

		InMemoryClassLoader(Map<String, InMemoryJavaFileObject> targets) {
			super(Thread.currentThread().getContextClassLoader());
			this.targets = targets;
		}

		@Override
		protected Class<?> findClass(String className) throws ClassNotFoundException {
			InMemoryJavaFileObject target = targets.get(className);
			if (target != null) {
				byte[] bytes = target.toByteArray();
				return defineClass(className, bytes, 0, bytes.length);
			}
			return super.findClass(className);
		}
	}
}
