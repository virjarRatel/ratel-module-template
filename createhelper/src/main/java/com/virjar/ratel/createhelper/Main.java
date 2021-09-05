package com.virjar.ratel.createhelper;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class Main {

    public static void main(String[] args) throws Exception {

        System.out.println("param: " + StringUtils.join(args, " "));
        final Options options = new Options();
        options.addOption(new Option("m", "moduleName", true, "the name of output project !!!"));
        options.addOption(new Option("p", "package", true, "in many case, we need crack a certain apk"));
        options.addOption(new Option("f", "force", false, "force overwrite"));
        options.addOption(new Option("t", "template-dir", true, "a project for template"));
        options.addOption(new Option("a", "apk", true, "path to apk"));
        options.addOption(new Option("h", "help", false, "show help message"));


        DefaultParser parser = new DefaultParser();

        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption('h')) {
            HelpFormatter hf = new HelpFormatter();
            hf.setWidth(110);
            hf.printHelp("create-helper", options);
            return;
        }

        Context context = new Context();

        ApkMeta apkMeta = null;
        if (cmd.hasOption('a')) {
            try (ApkFile apkFile = new ApkFile(new File(cmd.getOptionValue('a')))) {
                apkFile.setPreferredLocale(Locale.ENGLISH);
                apkMeta = apkFile.getApkMeta();
            }
        }

        // template-dir
        File templateDir = null;
        if (cmd.hasOption('t')) {
            templateDir = new File(cmd.getOptionValue('t'));
        } else {
            String userDir = System.getProperty("usr.dir");
            if (userDir == null) {
                userDir = new File(".").getAbsolutePath();
            }
            File searchDir = new File(userDir).getParentFile();
            templateDir = searchDir(searchDir);
        }

        if (templateDir == null || !templateDir.exists() || !templateDir.isDirectory()) {
            System.out.println("error template for : " + templateDir);
            HelpFormatter hf = new HelpFormatter();
            hf.setWidth(110);
            hf.printHelp("create-helper", options);
            return;
        }

        if (cmd.hasOption('m')) {
            context.moduleName = cmd.getOptionValue('m');
        } else if (apkMeta == null) {
            System.out.println("need module name");
            HelpFormatter hf = new HelpFormatter();
            hf.setWidth(110);
            hf.printHelp("create-helper", options);
            return;
        } else {
            String label = apkMeta.getLabel();
            boolean passed = true;
            for (char character : label.toCharArray()) {
                if (Character.isLetterOrDigit(character)) {
                    continue;
                }
                if (character == '-' || character == '_') {
                    continue;
                }
                passed = false;
                break;
            }
            if (passed) {
                context.moduleName = "crack-" + label;
            } else {
                context.moduleName = "crack-" + apkMeta.getPackageName().replaceAll("\\.", "-");
            }
        }
        final File modulePath = new File(templateDir.getParentFile(), context.moduleName);
        boolean force = false;
        if (cmd.hasOption('f')) {
            force = true;
        }
        if (modulePath.exists() && !force) {
            System.out.println("the module exist: " + modulePath.getAbsolutePath());
            return;
        } else {
            if (modulePath.exists()) {
                FileUtils.forceDelete(modulePath);
            }
        }
        if (!modulePath.mkdirs()) {
            System.out.println("create director failed: " + modulePath.getAbsolutePath());
            return;
        }

        final File finalTemplateDir = templateDir;
        FileUtils.copyDirectory(templateDir, modulePath, new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.getParentFile().equals(finalTemplateDir) && file.getName().equals("build")) {
                    return false;
                }
                if (file.getName().endsWith(".iml")) {
                    return false;
                }
                return true;
            }
        }, true);


        String applicationId;
        if (cmd.hasOption('p')) {
            applicationId = "ratel.crack." + cmd.getOptionValue('p');
        } else if (apkMeta != null) {
            applicationId = "ratel." + apkMeta.getPackageName();
        } else {
            applicationId = "ratel." + context.moduleName.replaceAll("-", ".");
        }


        context.applicationId = applicationId;
        if (apkMeta != null) {
            context.appName = "crack-" + apkMeta.getLabel();
        } else {
            context.appName = context.moduleName;
        }

        context.outDir = modulePath;
        if (cmd.hasOption('p')) {
            context.forApp = cmd.getOptionValue('p').trim();
        } else if (apkMeta != null) {
            context.forApp = apkMeta.getPackageName();
        }

        editBuildGradle(context);

        editAndroidManifest(context);

        migrateJavaCode(context);

        appendSettingGradleFile(context);

        editModuleAppName(context);
    }

    private static void editModuleAppName(Context context) throws Exception {
        File stringsXmlFile = new File(context.outDir, "src/main/res/values/strings.xml");
        Document document = loadDocument(stringsXmlFile);
        NodeList string = document.getElementsByTagName("string");
        for (int i = 0; i < string.getLength(); i++) {
            Node item = string.item(i);
            if (!(item instanceof Element)) {
                continue;
            }
            Element element = (Element) item;
            if (!element.getAttribute("name").equals("app_name")) {
                continue;
            }
            String appLabel = context.moduleName;
            if (!appLabel.startsWith("crack-")) {
                appLabel += "crack-";
            }
            element.setTextContent(appLabel);
        }
        saveDocument(stringsXmlFile, document);
    }

    private static void appendSettingGradleFile(Context context) throws IOException {
        File settingsGradleFile = new File(context.outDir.getParentFile(), "settings.gradle");
        if (!settingsGradleFile.exists()) {
            return;
        }
        List<String> strings = FileUtils.readLines(settingsGradleFile, StandardCharsets.UTF_8);

        for (String str : strings) {
            if (str.contains(context.moduleName)) {
                return;
            }
        }

        boolean hasAdd = false;
        for (int i = 0; i < strings.size(); i++) {
            String line = strings.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.trim().startsWith("include")) {
                continue;
            }
            strings.add(i, "        \':" + context.moduleName + "\',");
            hasAdd = true;
            break;
        }
        if (!hasAdd) {
            strings.add("        \':" + context.moduleName + "\',");
        }
        for (int i = strings.size() - 1; i > 0; i--) {
            String line = strings.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.trim().endsWith(",")) {
                String trim = line.trim();
                strings.set(i, trim.substring(0, trim.length() - 1));

            }
            break;

        }
        FileUtils.writeLines(settingsGradleFile, strings);
    }

    private static void migrateJavaCode(Context context) throws IOException {
        File dstJavaDir = new File(context.outDir, "src/main/java/" + context.applicationId.replaceAll("\\.", "/"));
        if (!dstJavaDir.mkdirs()) {
            throw new IOException("can not create dir: " + dstJavaDir.getAbsolutePath());
        }
        dstJavaDir.delete();
        FileUtils.moveDirectory(new File(context.outDir, "src/main/java/com/virjar/ratel/demoapp/crack"), dstJavaDir);
        if (!context.applicationId.startsWith("com.virjar")) {
            FileUtils.forceDelete(new File(context.outDir, "src/main/java/com/virjar"));
        }

        File[] files = dstJavaDir.listFiles();
        if (files == null) {
            return;
        }
        for (File javaFile : files) {
            if (!javaFile.getName().endsWith(".java")) {
                continue;
            }
            List<String> strings = FileUtils.readLines(javaFile, StandardCharsets.UTF_8);
            for (int i = 0; i < strings.size(); i++) {
                String line = strings.get(i);
                if (line.trim().startsWith("package ")) {
                    strings.set(i, "package " + context.applicationId + ";");
                    break;
                }
            }
            FileUtils.writeLines(javaFile, strings);
        }

        File xposedInitFile = new File(context.outDir, "src/main/assets/xposed_init");
        FileUtils.write(xposedInitFile, context.applicationId + ".HookEntry\r\n", StandardCharsets.UTF_8);
    }


    private static void editAndroidManifest(Context context) throws Exception {
        File manifestFile = new File(context.outDir, "src/main/AndroidManifest.xml");
        Document document = loadDocument(manifestFile);
        Element manifest = (Element) document.getElementsByTagName("manifest").item(0);
        manifest.setAttribute("package", context.applicationId);


        Element application = (Element) document.getElementsByTagName("application").item(0);
        NodeList childNodes = application.getChildNodes();
        // List<Element> applicationMetaList = new ArrayList<>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (!(item instanceof Element)) {
                continue;
            }
            Element element = (Element) item;
            if (!element.getTagName().equals("meta-data")) {
                continue;
            }
            String metaName = element.getAttribute("android:name");
            String mataValue = element.getAttribute("android:value");

            switch (metaName) {
                case "virtualEnvModel":
                    if (!context.multiAccount) {
                        application.removeChild(item);
                    } else {
                        element.setAttribute("android:value", "Multi");
                    }
                    break;
                case "for_ratel_apps":
                    if (context.forApp == null || context.forApp.trim().isEmpty()) {
                        application.removeChild(item);
                    } else {
                        element.setAttribute("android:value", context.forApp.trim());
                    }
                    break;
                case "xposeddescription":
                    element.setAttribute("android:value", "auto generated crack for: " + context.moduleName);
            }
        }

        Element activity = (Element) document.getElementsByTagName("activity").item(0);
        activity.setAttribute("android:name", context.applicationId + ".MainActivity");

        saveDocument(manifestFile, document);
    }

    private static void editBuildGradle(Context context) throws IOException {
        File buildGradleFile = new File(context.outDir, "build.gradle");
        List<String> strings = FileUtils.readLines(buildGradleFile, StandardCharsets.UTF_8);
        for (int i = 0; i < strings.size(); i++) {
            String line = strings.get(i);
            if (!line.contains("applicationId")) {
                continue;
            }
            int splitIndex = line.indexOf("applicationId") + "applicationId".length() + 1;
            strings.set(i, line.substring(0, splitIndex) + "\"" + context.applicationId + "\"");
        }
        FileUtils.writeLines(buildGradleFile, strings);
    }

    private static class Context {
        public String appName;
        public String applicationId;
        public File outDir;
        public String moduleName;
        boolean multiAccount = false;
        String forApp;
    }

    private static File searchDir(File dir) {
        if (dir.isFile()) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.getName().equalsIgnoreCase("crack-demoapp")) {
                return file;
            }
        }
        return null;
    }

    /**
     * @param file File to load into Document
     * @return Document
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    public static Document loadDocument(File file)
            throws IOException, SAXException, ParserConfigurationException {

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setFeature(FEATURE_DISABLE_DOCTYPE_DECL, true);
        docFactory.setFeature(FEATURE_LOAD_DTD, false);

        try {
            docFactory.setAttribute(ACCESS_EXTERNAL_DTD, " ");
            docFactory.setAttribute(ACCESS_EXTERNAL_SCHEMA, " ");
        } catch (IllegalArgumentException ex) {
            System.out.println("JAXP 1.5 Support is required to validate XML");
        }

        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        // Not using the parse(File) method on purpose, so that we can control when
        // to close it. Somehow parse(File) does not seem to close the file in all cases.
        FileInputStream inputStream = new FileInputStream(file);
        try {
            return docBuilder.parse(inputStream);
        } finally {
            inputStream.close();
        }
    }

    /**
     * @param file File to save Document to (ie AndroidManifest.xml)
     * @param doc  Document being saved
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     * @throws TransformerException
     */
    public static void saveDocument(File file, Document doc)
            throws IOException, SAXException, ParserConfigurationException, TransformerException {

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);
        transformer.transform(source, result);
    }

    private static final String ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD";
    private static final String ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema";
    private static final String FEATURE_LOAD_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final String FEATURE_DISABLE_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl";
}
