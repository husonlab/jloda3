/*
 * ArgsOptions.java Copyright (C) 2024 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package jloda.swing.util;

import jloda.swing.message.MessageWindow;
import jloda.util.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * command line arguments
 * Daniel Huson, 11.2013
 */
public class ArgsOptions {
    public final static String OTHER = "Other:";

    private boolean verbose;
    private final String programName;
    private final String description;
    private String version;
    private String authors;
    private String license;
    private final List<String> arguments;
    private final List<String> usage;

    private final Set<String> shortKeys = new HashSet<>();
    private final Set<String> longKeys = new HashSet<>();

    private boolean alreadyHasOtherComment = false;

    private boolean commandMandatory = false;

    private boolean doHelp = false;

    private static MessageWindow messageWindow = null;

    private boolean optionFound = false;

    private boolean doLaTeX = false;
    private String latexDescription = "";

    /**
     * constructor
     *
     * @param args        command line arguments
     * @param main        class that contains main method
     * @param description program description
     */
    public ArgsOptions(String[] args, Object main, String description) throws CanceledException {
        this(args, main, (ProgramProperties.getProgramName() != null && ProgramProperties.getProgramName().length() > 0 ? ProgramProperties.getProgramName() : (main != null ? Basic.getShortName(main.getClass()) : "Unknown")), description);
    }

    /**
     * constructor
     *
     * @param args        command line arguments
     * @param main        class that contains main method
     * @param description program description
     */
    public ArgsOptions(String[] args, Object main, String programName, String description) throws CanceledException {
        if (args.length > 0 && args[args.length - 1].equals("--argsGui")) {
            args = getDialogInput(args, args.length - 1);
        }
        arguments = new ArrayList<>(Arrays.asList(args));

        this.programName = programName;
        if (main != null)
            this.version = Basic.getVersion(main.getClass(), programName);
        this.description = description;

        usage = new LinkedList<>();


        try {
            doHelp = (args.length > 0 && args[0].equalsIgnoreCase("help")) || getOption("-h", "--help", "Show help", false, false);
            setVerbose(getOption("-v", "--verbose", "verbose", false) && !doHelp);
        } catch (UsageException ignored) {
        }
        try {
            doLaTeX = (args.length > 0 && args[0].equalsIgnoreCase("latex")) || getOption("!latex", "!--latex", "Generate LaTeX section", false, false);
            if (doLaTeX) {
                doHelp = true;
            }
        } catch (UsageException ignored) {
        }

        if (verbose)
            System.err.println(programName + " - " + getDescription() + "\nOptions:");
    }

    public static Command createCommand(String name, String description) {
        return new Command(name, description);
    }

    private String additionalUsage;

    public String getAdditionalUsage() {
        return additionalUsage;
    }

    public void setAdditionalUsage(String additionalUsage) {
        this.additionalUsage = additionalUsage;
    }

    /**
     * get description
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    public String getUsage() {
        var result = new StringBuilder();
        result.append("SYNOPSIS\n");
        result.append("\t").append(programName).append(commandMandatory ? " command" : "").append(" [options]\n");
        result.append("DESCRIPTION\n");
        result.append("\t").append(getDescription()).append("\n");

        result.append("OPTIONS\n");

        for (String line : usage) {
            if (line.contains("--verbose") || line.contains("--help"))
                continue;
            result.append(replaceFirstColon(line)).append("\n");
        }
        result.append(replaceFirstColon("\t-v, --verbose: Echo commandline options and be verbose. Default value: false.\n"));
        result.append(replaceFirstColon("\t-h, --help: Show program usage and quit.\n"));
        if (authors != null)
            result.append("AUTHOR(s)\n\t").append(authors).append(".\n");

        if (version != null)
            result.append("VERSION\n\t").append(version).append(".\n");

        if (license != null)
            result.append(license).append(".\n");

        if (getAdditionalUsage() != null)
            result.append(getAdditionalUsage());

        return result.toString();
    }

    public boolean isDoHelp() {
        return doHelp;
    }

    private String replaceFirstColon(String line) {
        StringBuilder buf = new StringBuilder();
        int pos = 0;
        while (pos < line.length()) {
            if (line.charAt(pos) == ':')
                break;
            buf.append(line.charAt(pos));
            pos++;
        }
        if (pos == line.length() - 1) // colon is last character, keep
            buf.append(":");
        else {      // replace by two or more spaces
            buf.append("  ");
            int top = Math.min(35, line.length());
            buf.append(" ".repeat(Math.max(0, top - pos)));
            pos++;
            while (pos < line.length()) {
                buf.append(line.charAt(pos));
                pos++;
            }
        }
        return buf.toString();
    }

    /**
     * call this once all arguments have been parsed. Quit on help
     */
    public void done() throws UsageException {
        if (!alreadyHasOtherComment)
            comment(OTHER);

        if (doLaTeX) {
            System.out.println(getLatexSection());
            System.exit(0);
        }

        if (verbose) {
            System.err.println("\t--verbose: true");
        }

        if (!doHelp) {
            if (version != null)
                System.err.println("Version   " + version);
            if (authors != null)
                System.err.println("Author(s) " + authors);
            if (license != null)
                System.err.println(license);
        }


        if (doHelp) {
            System.err.println(getUsage());
            if (!hasMessageWindow())
                System.exit(0);
            else
                throw new UsageException("Help");
        }
        if (!arguments.isEmpty()) {
            StringBuilder message = new StringBuilder("Invalid, unknown or duplicate option:");
            for (String arg : arguments) {
                message.append(" ").append(arg);
            }
            message.append("\n");
            throw new UsageException(message.toString());
        }

        System.err.println("Java version: " + System.getProperty("java.version") + "; max memory: " + PeakMemoryUsageMonitor.getPeakUsageString());
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean reportValues) {
        this.verbose = reportValues;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    /**
     * add a comment to the usage message
     */
    public void comment(String comment) {
        usage.add(" " + comment);
        if (verbose)
            System.err.println(comment);
        if (comment.equals(OTHER))
            alreadyHasOtherComment = true;
    }

    /**
     * gets a command (first value in arguments)
     *
     * @return command
     */
    public String getCommand(String... legalValues) throws UsageException {
        final Command[] pairs = new Command[legalValues.length];
        for (int i = 0; i < legalValues.length; i++)
            pairs[i] = new Command(legalValues[i], null);
        return getCommand(pairs);
    }

    /**
     * gets a command (first value in arguments)
     *
     * @return command
     */
    public String getCommand(Command... pairs) throws UsageException {
        final String[] legalValues = new String[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            legalValues[i] = pairs[i].getFirst();
        }
        usage.add(" Command " + StringUtils.toString(legalValues, " | "));

        for (final Pair<String, String> pair : pairs) {
            if (pair.getSecond() != null)
                usage.add("\t" + pair.getFirst() + ": " + pair.getSecond());
        }

        final String command;
        if (!isDoHelp()) {
            if (arguments.isEmpty())
                throw new UsageException("Command expected, must be one of: " + StringUtils.toString(legalValues, ", "));
            command = arguments.remove(0);
            if (!CollectionUtils.contains(legalValues, command))
                throw new UsageException("Command: " + command + ": must be one of: " + StringUtils.toString(legalValues, ", "));

            if (verbose)
                System.err.println("\tcommand: " + command);

            if (command.equals("help"))
                doHelp = true;

            return command;
        } else
            return "";
    }

    public boolean getOption(String shortKey, String longKey, String description, boolean defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, false);
    }

    public boolean getOptionMandatory(String shortKey, String longKey, String description, boolean defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, true);
    }

    public byte getOption(String shortKey, String longKey, String description, Byte defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, false).byteValue();
    }

    public byte getOptionMandatory(String shortKey, String longKey, String description, Byte defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, true).byteValue();
    }

    public int getOption(String shortKey, String longKey, String description, Integer defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, false).intValue();
    }

    public int getOption(String shortKey, String longKey, String description, int defaultValue, int low, int high) throws UsageException {
        int result = getOption(shortKey, longKey, description, defaultValue, false).intValue();
        if (!doHelp && (result < low || result > high))
            throw new UsageException("Option " + longKey + ": value=" + result + ": out of range: " + low + " - " + high);
        return result;
    }

    public int getOptionMandatory(String shortKey, String longKey, String description, int defaultValue, int low, int high) throws UsageException {
        int result = getOption(shortKey, longKey, description, defaultValue, true).intValue();
        if (!doHelp && (result < low || result > high))
            throw new UsageException("Option " + longKey + ": value=" + result + ": out of range: " + low + " - " + high);
        return result;
    }

    public int getOptionMandatory(String shortKey, String longKey, String description, Integer defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, true).intValue();
    }

    public long getOption(String shortKey, String longKey, String description, Long defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, false).longValue();
    }

    public long getOptionMandatory(String shortKey, String longKey, String description, Long defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, true).longValue();
    }

    public float getOption(String shortKey, String longKey, String description, Float defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, false).floatValue();
    }

    public float getOption(String shortKey, String longKey, String description, Float defaultValue, float low, float high) throws UsageException {
        float result = getOption(shortKey, longKey, description, defaultValue, false).floatValue();
        if (!doHelp && (result < low || result > high))
            throw new UsageException("Option " + longKey + ": value=" + result + ": out of range: " + low + " - " + high);
        return result;
    }

    public float getOptionMandatory(String shortKey, String longKey, String description, Float defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, true).floatValue();
    }

    public double getOption(String shortKey, String longKey, String description, Double defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, false).doubleValue();
    }

    public double getOptionMandatory(String shortKey, String longKey, String description, Double defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, defaultValue, true).doubleValue();
    }

    public String getOption(String shortKey, String longKey, String description, String defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, null, defaultValue, false);
    }

    public String getOptionMandatory(String shortKey, String longKey, String description, String defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, null, defaultValue, true);
    }

    public String getOption(String shortKey, String longKey, String description, Object[] legalValues, String defaultValue) throws UsageException {
        List<String> strings = new LinkedList<>();
        for (Object v : legalValues)
            strings.add(v.toString());
        return getOption(shortKey, longKey, description, strings, defaultValue, false);
    }

    public String getOptionMandatory(String shortKey, String longKey, String description, Object[] legalValues, String defaultValue) throws UsageException {
        List<String> strings = new LinkedList<>();
        for (Object v : legalValues)
            strings.add(v.toString());
        return getOption(shortKey, longKey, description, strings, defaultValue, true);
    }

    public String getOption(String shortKey, String longKey, String description, Collection<?> legalValues, String defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, Arrays.asList(StringUtils.toStrings(legalValues)), defaultValue, false);
    }

    public String getOptionMandatory(String shortKey, String longKey, String description, Collection<?> legalValues, String defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, Arrays.asList(StringUtils.toStrings(legalValues)), defaultValue, true);
    }

    public List<String> getOption(String shortKey, String longKey, String description, List<String> defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, null, defaultValue, false);
    }

    public List<String> getOptionMandatory(String shortKey, String longKey, String description, List<String> defaultValue) throws UsageException {
        return getOption(shortKey, longKey, description, null, defaultValue, true);
    }

    public String[] getOption(String shortKey, String longKey, String description, String[] defaultValue) throws UsageException {
        List<String> result = getOption(shortKey, longKey, description, null, Arrays.asList(defaultValue), false);
        return result.toArray(new String[0]);
    }

    public String[] getOptionMandatory(String shortKey, String longKey, String description, String[] defaultValue) throws UsageException {
        List<String> result = getOption(shortKey, longKey, description, null, Arrays.asList(defaultValue), true);
        return result.toArray(new String[0]);
    }

    public String[] getOption(String shortKey, String longKey, String description, String[] legalValues, String[] defaultValue) throws UsageException {
        List<String> result = getOption(shortKey, longKey, description, Arrays.asList(legalValues), Arrays.asList(defaultValue), false);
        return result.toArray(new String[0]);
    }

    public String[] getOptionMandatory(String shortKey, String longKey, String description, String[] legalValues, String[] defaultValue) throws UsageException {
        List<String> result = getOption(shortKey, longKey, description, Arrays.asList(legalValues), Arrays.asList(defaultValue), true);
        return result.toArray(new String[0]);
    }


    public Number getOption(String shortKey, String longKey, String description, Number defaultValue, boolean mandatory) throws UsageException {
        if (!shortKey.startsWith("-"))
            shortKey = "-" + shortKey;
        if (!longKey.startsWith("-"))
            longKey = "--" + longKey;

        if (shortKeys.contains(shortKey))
            throw new RuntimeException("Internal error: multiple definitions of short key: " + shortKey);
        else
            shortKeys.add(shortKey);
        if (longKeys.contains(longKey))
            throw new RuntimeException("Internal error: multiple definitions of long key: " + longKey);
        else
            longKeys.add(longKey);

        usage.add("\t" + shortKey + ", " + longKey + " [number]: " + description + ". " + (mandatory ? "Mandatory option." : "Default value: " + defaultValue + "."));

        Number result = defaultValue;

        optionFound = false;
        Iterator<String> it = arguments.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals(shortKey) || arg.equals(longKey)) {
                it.remove();
                if (!it.hasNext()) {
                    throw new UsageException("Value for option " + longKey + ": not found");
                }
                result = getNumber(defaultValue, it.next());
                it.remove();
                optionFound = true;
                break;
            }
        }
        if (!optionFound) {
            if (mandatory && !doHelp)
                throw new UsageException("Mandatory option '" + longKey + "' not specified");
        }
        if (verbose)
            System.err.println("\t" + longKey + ": " + result);
        return result;
    }

    private boolean getOption(String shortKey, String longKey, String description, boolean defaultValue, boolean mandatory) throws UsageException {
        if (shortKey.equals("+g"))
            shortKey = "-g"; // for backward compatibility

        boolean hide = false;
        if (shortKey.startsWith("!")) {
            hide = true;
            shortKey = shortKey.substring(1);
        }

        if (!shortKey.startsWith("-") && !shortKey.startsWith("+"))
            shortKey = "-" + shortKey;
        if (!longKey.startsWith("-"))
            longKey = "--" + longKey;

        if (shortKeys.contains(shortKey))
            throw new RuntimeException("Internal error: multiple definitions of short key: " + shortKey);
        else
            shortKeys.add(shortKey);
        if (longKeys.contains(longKey))
            throw new RuntimeException("Internal error: multiple definitions of long key: " + longKey);
        else
            longKeys.add(longKey);

        if (!hide)
            usage.add("\t" + shortKey + ", " + longKey + ": " + description + ". " + (mandatory ? "Mandatory option." : "Default value: " + defaultValue + "."));

        boolean result = false;
        optionFound = false;
        Iterator<String> it = arguments.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals(shortKey) || arg.equals(longKey)) {
                it.remove();
                if (!it.hasNext()) {
                    result = !defaultValue;
                    optionFound = true;
                    break;
                }
                String value = it.next();
                if (value.length() > 0 && (value.startsWith("-") || value.startsWith("+"))) {
                    result = !defaultValue;
                    optionFound = true;
                    break;
                }
                it.remove();
                result = Boolean.parseBoolean(value);
                optionFound = true;
                break;
            }
        }
        if (!optionFound) {
            if (mandatory && !doHelp)
                throw new UsageException("Mandatory option '" + longKey + "' not specified");
            else
                result = defaultValue;
        }
        if (!hide && verbose)
            System.err.println("\t" + longKey + ": " + result);
        return result;
    }

    public String getOption(String shortKey, String longKey, String description, Collection<String> legalValues, String defaultValue, boolean mandatory) throws UsageException {
        boolean hide = false;
        if (shortKey.startsWith("!")) {
            hide = true;
            shortKey = shortKey.substring(1);
        }
        if (!shortKey.startsWith("-"))
            shortKey = "-" + shortKey;
        if (!longKey.startsWith("-"))
            longKey = "--" + longKey;

        if (shortKeys.contains(shortKey))
            throw new RuntimeException("Internal error: multiple definitions of short key: " + shortKey);
        else
            shortKeys.add(shortKey);
        if (longKeys.contains(longKey))
            throw new RuntimeException("Internal error: multiple definitions of long key: " + longKey);
        else
            longKeys.add(longKey);

        String defaultValueString = (defaultValue.isEmpty() ? "" : "Default value: " + defaultValue + ".");

        if (!hide)
            usage.add("\t" + shortKey + ", " + longKey + " [string]: " + description + ". " + (mandatory ? "Mandatory option." : defaultValueString)
                      + (legalValues != null ? " Legal values: " + StringUtils.toString(legalValues, ", ") : ""));

        String result = defaultValue;

        optionFound = false;
        final Iterator<String> it = arguments.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals(shortKey) || arg.equals(longKey)) {
                it.remove();
                if (!it.hasNext()) {
                    throw new UsageException("Value for option " + longKey + ": not found");
                }
                result = it.next();
                it.remove();
                optionFound = true;
                if (legalValues != null && !legalValues.contains(result))
                    throw new UsageException("Illegal value for option " + longKey + ": " + result + ", legal values: " + StringUtils.toString(legalValues, ", "));

                break;
            }
        }
        if (!optionFound) {
            if (mandatory && !doHelp)
                throw new UsageException("Mandatory option '" + longKey + "' not specified" + (legalValues != null ? ", legal values: " + StringUtils.toString(legalValues, ", ") : "."));
        }
        if (!hide && verbose && !result.isEmpty())
            System.err.println("\t" + longKey + ": " + result);
        return result;
    }

    private List<String> getOption(String shortKey, String longKey, String description, Collection<String> legalValues, List<String> defaultValue, boolean mandatory) throws UsageException {
        boolean hide = false;
        if (shortKey.startsWith("!")) {
            hide = true;
            shortKey = shortKey.substring(1);
        }
        if (!shortKey.startsWith("-"))
            shortKey = "-" + shortKey;
        if (!longKey.startsWith("-"))
            longKey = "--" + longKey;

        if (shortKeys.contains(shortKey))
            throw new RuntimeException("Internal error: multiple definitions of short key: " + shortKey);
        else
            shortKeys.add(shortKey);
        if (longKeys.contains(longKey))
            throw new RuntimeException("Internal error: multiple definitions of long key: " + longKey);
        else
            longKeys.add(longKey);

        var defaultValueString = (defaultValue.isEmpty() ? "" : "Default value(s): '" + StringUtils.toString(defaultValue, "' '") + "'");

        if (!hide) {
            if (mandatory)
                usage.add("\t" + shortKey + ", " + longKey + " [string(s)]: " + description + ". Mandatory option" + (legalValues != null ? " legal values: " + StringUtils.toString(legalValues, ", ") : "") + ".");
            else
                usage.add("\t" + shortKey + ", " + longKey + " [string(s)]: " + description + ". " + defaultValueString + (legalValues != null ? " legal values: " + StringUtils.toString(legalValues, ", ") + "." : defaultValueString.length() > 0 ? "." : ""));

        }
        List<String> result = new LinkedList<>();
        boolean inArguments = false; // once in arguments, will continue until argument starts with -

        optionFound = false;
        Iterator<String> it = arguments.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals(shortKey) || arg.equals(longKey)) {
                it.remove();
                inArguments = true;
                optionFound = true;
            }
            if (inArguments) {
                boolean done = false;
                while (it.hasNext()) {
                    String value = it.next();
                    if (!value.isEmpty() && (value.startsWith("-") || value.startsWith("+"))) {
                        done = true;
                        break;
                    }
                    it.remove();
                    if (legalValues != null && !legalValues.contains(value))
                        throw new UsageException("Illegal value for option " + longKey + ": " + value + ", legal values: " + StringUtils.toString(legalValues, ", "));

                    result.add(value);
                }
                if (done)
                    break;
            }
        }
        if (!inArguments) {
            if (mandatory && !doHelp)
                throw new UsageException("Mandatory option '" + longKey + "' not specified");
            else
                result = defaultValue;
        }
        if (!hide && verbose && !result.isEmpty())
            System.err.println("\t" + longKey + ": '" + StringUtils.toString(result, "' '") + "'");
        return result;
    }

    /**
     * return number from value as object same as defaultValue
     *
     * @return appropriate number object
     */
    private static Number getNumber(Number defaultValue, String value) {
        Number result = null;
        if (defaultValue instanceof Byte) {
            result = Byte.parseByte(value);
        } else if (defaultValue instanceof Short) {
            result = Short.parseShort(value);
        } else if (defaultValue instanceof Integer) {
            result = Integer.parseInt(value);
        } else if (defaultValue instanceof Long) {
            result = Long.parseLong(value);
        } else if (defaultValue instanceof Float) {
            result = Float.parseFloat(value);
        } else if (defaultValue instanceof Double) {
            result = Double.parseDouble(value);
        }
        return result;
    }

    /**
     * present a dialog box and get the commandline input from it
     *
     * @return commands
     */
    private String[] getDialogInput(String[] args, int argsLength) throws CanceledException {
        JOptionPane pane = new JOptionPane("Enter command-line options (-h for help)", JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, ProgramProperties.getProgramIcon(), null, "");
        pane.setWantsInput(true);
        pane.setInitialSelectionValue(StringUtils.toString(args, 0, argsLength, " "));

        JDialog dialog = pane.createDialog(null, "Input " + ProgramProperties.getProgramName());
        dialog.setResizable(true);
        dialog.setSize(600, 175);
        dialog.setVisible(true);

        if ((Integer) pane.getValue() == JOptionPane.CANCEL_OPTION)
            throw new CanceledException();

        String result = pane.getInputValue().toString();

        messageWindow = new MessageWindow(ProgramProperties.getProgramIcon(), "Messages " + ProgramProperties.getProgramName(), null, false);
        messageWindow.getFrame().setSize(600, 400);
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        messageWindow.getFrame().setLocation(dim.width / 2 - messageWindow.getFrame().getSize().width / 2, dim.height / 2 - messageWindow.getFrame().getSize().height / 2);
        messageWindow.setVisible(true);
        messageWindow.getFrame().setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        System.err.println("Input: " + result);

        //String result=(String)JOptionPane.showInputDialog(null, "Enter command-line options", "Input "+ProgramProperties.getProgramName(),JOptionPane.QUESTION_MESSAGE, ProgramProperties.getProgramIcon(), null, oldInput);


        //String result= JOptionPane.showInputDialog(null,"Enter command-line options",oldInput);

        if (!result.trim().isEmpty()) {
            result = result.trim().replaceAll("\\s+", " ");
            return result.split(" ");
        } else
            return new String[0];
    }

    /**
     * do we have a message window?
     *
     * @return true, if message window open and visible
     */
    public static boolean hasMessageWindow() {
        return messageWindow != null && messageWindow.isVisible();
    }

    public boolean optionWasExplicitlySet() {
        return optionFound;
    }

    public boolean isCommandMandatory() {
        return commandMandatory;
    }

    public void setCommandMandatory(boolean commandMandatory) {
        this.commandMandatory = commandMandatory;
    }

    public static class Command extends Pair<String, String> {
        public Command(String name, String description) {
            super(name, description);
        }
    }

    public void setLatexDescription(String latexDescription) {
        this.latexDescription = latexDescription;
    }

    public String getLatexSection() {
        var title = StringUtils.fromCamelCase(Basic.getMainClassName()).replace("A Add", "AAdd")
                .replaceAll("\\s*3\\s*", "3").replaceAll("\\s*2\\s*", " to ");
        var toolName = title.toLowerCase().replaceAll(" to ", "2").replaceAll(" ", "-");
        return "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n"
               + "\\section{%s}%n%n".formatted(title) +
               "The \\verb^%s^ commandline program. \\index{%s program}%n%n".formatted(toolName, toolName) +
               latexDescription + "\n\n{\\footnotesize\n\\begin{Verbatim}[breaklines=true]\n" +
               StringUtils.getTextBefore("AUTHOR(s)", getUsage().replaceAll("Default value: /.*/", "Default value: "))
               + "\\end{Verbatim}\n}\n\n";
    }
}
