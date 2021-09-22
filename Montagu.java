package ocotillo.samples.parsers;

import java.awt.Color;
import java.io.File;
import java.util.*;

import ocotillo.dygraph.DyEdgeAttribute;
import ocotillo.dygraph.DyGraph;
import ocotillo.dygraph.DyNodeAttribute;
import ocotillo.dygraph.Evolution;
import ocotillo.dygraph.FunctionConst;
import ocotillo.geometry.Coordinates;
import ocotillo.geometry.Interval;
import ocotillo.graph.Edge;
import ocotillo.graph.Node;
import ocotillo.graph.StdAttribute;
import ocotillo.samples.parsers.Commons.DyDataSet;
import ocotillo.samples.parsers.Commons.Mode;
import ocotillo.serialization.ParserTools;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;


public class Montagu {
    private static class LettersDataSet {

        public List<Letter> lettersList = new ArrayList<>();
        public Map<Integer, Person> personsMap = new HashMap<>();
        public List<Person> personsList = new ArrayList<>();
        public int firstTime;
        public int lastTime;
    }

    private static class Letter implements Comparable<Letter> {

        int sourceID;
        int targetID;
        int date;


        public Letter(int sourceID, int targetID, int dateTime) {
            this.sourceID = sourceID;
            this.targetID = targetID;
            this.date = dateTime;

        }

        @Override
        public int compareTo(Letter o) {
            return Integer.compare(this.date, o.date);
        }

        @Override
        public String toString() {
            return "Letter{" +
                    "sourceID=" + sourceID +
                    ", targetID=" + targetID +
                    ", date=" + date +
                    '}';
        }
    }

    private static class Person {
        String name;
        int ID;

        public Person(String name, int ID) {
            this.name = name;
            this.ID = ID;
        }
    }

    /**
     * Produces the dynamic dataset for this data.
     *
     * @param mode the desired mode.
     * @return the dynamic dataset.
     */
    public static DyDataSet parse(Mode mode) {
        File personsFile = new File("data/Montagu/letter_people_as_sequence.txt");
        File lettersFile = new File("data/Montagu/letters_sent_as_sequence.txt");
        LettersDataSet dataset = parseLetters(personsFile, lettersFile);
        return new DyDataSet(
                parseGraph(personsFile, lettersFile, 0.5, mode),
                1,
                Interval.newClosed(dataset.firstTime,
                        dataset.lastTime));
    }

    /**
     * Parses the dialog sequence graph.
     *
     * @param personsFile the persons file.
     * @param lettersFile the letters file
     * @param halfLetterExchangeDuration the factor that encodes half the duration of a letter exchange.
     * One corresponds to the gap between consecutive letters.
     * @param mode the desired mode.
     * @return the dynamic graph.
     */
    public static DyGraph parseGraph(File personsFile, File lettersFile, double halfLetterExchangeDuration, Mode mode){
        DyGraph graph = new DyGraph();
        DyNodeAttribute<Boolean> presence = graph.nodeAttribute(StdAttribute.dyPresence);
        DyNodeAttribute<String> label = graph.nodeAttribute(StdAttribute.label);
        DyNodeAttribute<Coordinates> position = graph.nodeAttribute(StdAttribute.nodePosition);
        DyNodeAttribute<Color> color = graph.nodeAttribute(StdAttribute.color);
        DyNodeAttribute<Coordinates> nodeSize = graph.nodeAttribute(StdAttribute.nodeSize);
        DyEdgeAttribute<Boolean> edgePresence = graph.edgeAttribute(StdAttribute.dyPresence);
        DyEdgeAttribute<Color> edgeColor = graph.edgeAttribute(StdAttribute.color);

        LettersDataSet dataset = parseLetters(personsFile, lettersFile);
        Map<String, Node> nodeMap = new HashMap<>();
        for (Person person : dataset.personsList) {
            if(person.name.equals("Elizabeth Robinson Montagu")){
                Node node = graph.newNode(person.name);
                presence.set(node, new Evolution<>(false));
                label.set(node, new Evolution<>(person.name));
                position.set(node, new Evolution<>(new Coordinates(0, 0)));
                color.set(node, new Evolution<>(new Color(179, 21, 56, 158)));
                nodeSize.set(node, new Evolution<>(new Coordinates(1.5, 1.5)));
                nodeMap.put(person.name, node);
                continue;
            }
            Node node = graph.newNode(person.name);
            presence.set(node, new Evolution<>(false));
            label.set(node, new Evolution<>(person.name));
            position.set(node, new Evolution<>(new Coordinates(0, 0)));
            color.set(node, new Evolution<>(new Color(141, 211, 199)));
            nodeMap.put(person.name, node);
        }

        for (Letter letter : dataset.lettersList) {
            Node source = nodeMap.get(dataset.personsMap.get(letter.sourceID).name);
            Node target = nodeMap.get(dataset.personsMap.get(letter.targetID).name);
            Edge edge = graph.betweenEdge(source, target);
            if (edge == null) {
                edge = graph.newEdge(source, target);
                edgePresence.set(edge, new Evolution<>(false));
                edgeColor.set(edge, new Evolution<>(new Color(169, 166, 166)));
            }

            Interval participantPresence = Interval.newRightClosed(
                    dataset.firstTime, dataset.lastTime);
            Interval letterInterval = Interval.newRightClosed(
                    letter.date - halfLetterExchangeDuration,
                    letter.date + halfLetterExchangeDuration);

            presence.get(source).insert(new FunctionConst<>(participantPresence, true));
            presence.get(target).insert(new FunctionConst<>(participantPresence, true));
            edgePresence.get(edge).insert(new FunctionConst<>(letterInterval, true));
        }

        Commons.scatterNodes(graph, 200);
        Commons.mergePresenceFunctions(graph,
                dataset.firstTime - 1,
                dataset.lastTime + 1,
                mode);
        return graph;
    }

    private static LettersDataSet parseLetters(File personsFile, File lettersFile){
        LettersDataSet lettersDataSet = new LettersDataSet();
        parsePersonsFile(personsFile, lettersDataSet);
        parseLettersFile(lettersFile, lettersDataSet);
        return lettersDataSet;

    }

    private static void parsePersonsFile(File personsFile, LettersDataSet letterDataSet){
        List<String> fileLines = ParserTools.readFileLines(personsFile);
        Map<Integer, Person> personMap = new HashMap<>();
        List<Person> personsList = new ArrayList<>();
        letterDataSet.personsMap = personMap;
        letterDataSet.personsList = personsList;
        for(String personString : fileLines){
            Person newPerson = parsePerson(personString);
            personMap.put(newPerson.ID, newPerson);
            personsList.add(newPerson);
        }
    }

    private static Person parsePerson(String personString){
        String[] tokens = personString.split(",");
        int ID = Integer.parseInt(tokens[0]);
        String name = tokens[1].replaceAll("^\"|\"$", "");
        return new Person(name, ID);
    }

    private static void parseLettersFile(File lettersFile, LettersDataSet letterDataSet){
        List<String> fileLines = ParserTools.readFileLines(lettersFile);
        List<Letter> lettersList = new ArrayList<>();
        letterDataSet.lettersList = lettersList;
        int firstTime = Integer.MAX_VALUE;
        int lastTime = Integer.MIN_VALUE;
        for(String letterString : fileLines){
            Letter newLetter = parseLetter(letterString);
            firstTime = Math.min(firstTime, newLetter.date);
            lastTime = Math.max(lastTime, newLetter.date);
            lettersList.add(parseLetter(letterString));
        }
        letterDataSet.firstTime = firstTime;
        letterDataSet.lastTime = lastTime;
        Collections.sort(lettersList);
    }

    private static Letter parseLetter(String letterString){
        String[] tokens = letterString.split(",");
        int date = parseDate(tokens[2]);
        return new Letter(
                Integer.parseInt(tokens[0]),
                Integer.parseInt(tokens[1]),
                date
        );
    }

    private static int parseDate(String dateString){
        String[] dateTokens = dateString.split("-");
        int year = Integer.parseInt(dateTokens[0]);
        int month = Integer.parseInt(dateTokens[1]);
        int day = Integer.parseInt(dateTokens[2]);
        return year;
    }
}
