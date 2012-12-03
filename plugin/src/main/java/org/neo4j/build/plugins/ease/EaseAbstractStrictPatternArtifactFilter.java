/**
 * Licensed to Neo Technology under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Neo Technology licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.neo4j.build.plugins.ease;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.artifact.filter.AbstractStrictPatternArtifactFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * Filter to include or exclude artifacts from a list of patterns. The artifact pattern syntax is of the form:
 * 
 * <pre>
 * [groupId][:artifactId][:type][:version]
 * </pre>
 * or
 * <pre>
 * [groupId][:artifactId][:type][:classifier:][version]
 * </pre>
 * 
 * <p>
 * Where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
 * segment is treated as an implicit wildcard. When using the second form, i.e. specifying a classifier, the 
 * classifier needs to be terminated with a colon even if no version follows. This is necessary to distinguish
 * between the first and second form.
 * </p>
 * 
 * <p>
 * For example, <code>org.apache.*</code> would match all artifacts whose group id started with
 * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> would match all snapshot artifacts.
 * Further, <code>:::client:</code> would match all artifacts with classifier 'client'.
 * </p>
 * 
 * @author Florian Zschocke
 */
public abstract class EaseAbstractStrictPatternArtifactFilter extends AbstractStrictPatternArtifactFilter
{
    // static fields ----------------------------------------------------------

    static final Pattern classifierPatternRegex = Pattern.compile("([^:]*:[^:]*:[^:]*:)([^:]*):(.*)");

    // fields -----------------------------------------------------------------

    /**
     * A map of classifiers to corresponding patterns (with the classifier removed).
     */
    private final Map<Classifier, EaseStrictPatternArtifactMatcher> classifierPatterns;

    /**
     * Whether this filter should include or exclude artifacts that match the patterns.
     */
    private final boolean include;

    // constructors -----------------------------------------------------------

    /**
     * Creates a new filter that matches the specified artifact patterns and includes or excludes them according to the
     * specified flag.
     * 
     * @param patterns
     *            the list of artifact patterns to match, as described above
     * @param include
     *            <code>true</code> to include artifacts that match the patterns, or <code>false</code> to exclude
     *            them
     */
    public EaseAbstractStrictPatternArtifactFilter( List<String> patterns, boolean include )
    {
        super(filterOutClassifierPatterns(patterns), include);

        Map<Classifier, List<String>> classifierPatternMap = null;
        for ( String pattern : patterns ) {
            Matcher m = classifierPatternRegex.matcher(pattern);
            if ( m.matches() )
            {
                // A classifier present. Save the pattern, without the classifier, in our map.
                if (classifierPatternMap == null)
                {
                    classifierPatternMap = new HashMap<Classifier, List<String>>(patterns.size());
                }

                Classifier cl = new Classifier(m.group(2));
                List<String> classPatterns = classifierPatternMap.get(cl);
                if (classPatterns == null)
                {
                    classPatterns = new ArrayList<String>();
                    classifierPatternMap.put(cl, classPatterns);
                }

                classPatterns.add(m.group(1) + m.group(3));
            }
        }


        this.include = include;

        if ( classifierPatternMap == null )
        {
            this.classifierPatterns = Collections.emptyMap();
        }
        else 
        {
            // Create a map that holds pattern artifact matchers for each classifier so that
            // we reuse them later and don't have to instantiate one for each artifact/classifier.
            this.classifierPatterns = new HashMap<Classifier, EaseStrictPatternArtifactMatcher>(classifierPatternMap.size());
            for ( Entry<Classifier, List<String>> entry : classifierPatternMap.entrySet() )
            {
                EaseStrictPatternArtifactMatcher matcher = new EaseStrictPatternArtifactMatcher(entry.getValue());
                this.classifierPatterns.put(entry.getKey(), matcher);
            }
        }
    }

    // ArtifactFilter methods -------------------------------------------------

    /*
     * @see org.apache.maven.artifact.resolver.filter.ArtifactFilter#include(org.apache.maven.artifact.Artifact)
     */
    public boolean include( Artifact artifact )
    {
        boolean matched = false;

        if ( artifact.hasClassifier() )
        {
            // The artifact has a classifier. See if any of our classifier patterns match.
            for ( Entry<Classifier, EaseStrictPatternArtifactMatcher> entry : classifierPatterns.entrySet() )
            {
                Matcher m = entry.getKey().pattern.matcher(artifact.getClassifier());

                if ( m.matches() )
                {
                    // The classifier matched, so match the rest of the patterns.
                    matched = entry.getValue().matches(artifact);
                    if ( matched )
                    {
                        return include ? matched : !matched;
                    }
                }
            }
        }
        
        // Artifact has no classifier or no classifier pattern matched. Run normal match.
        return super.include(artifact);
    }

    // private methods --------------------------------------------------------

    /**
     * Filters out patterns that include a classifier from a list of pattern strings.
     * 
     * @param patterns
     * 			A list of strings with artifact id patterns that may include a classifier.
     * @return	The list of pattern strings with those removed that included a classifier.
     */
    static private List<String> filterOutClassifierPatterns(List<String> patterns)
    {
        List<String> normalPatterns = new ArrayList<String>(patterns.size());
        for (String pattern : patterns) {
            Matcher m = classifierPatternRegex.matcher(pattern);
            if (! m.matches())
            {
                normalPatterns.add(pattern);
            }
        }
        return normalPatterns;
    }


    // private helper classes -------------------------------------------------
    
    /**
     * Wrapper class used as key in the map mapping from classifier to 
     * pattern list or artifact matcher. This class combines the classifier
     * string and the regex pattern in a tuple. Storing the compiled regex
     * with the string as the key means it can be easily reused by the 
     * matching loop.
     *
     */
    static final private class Classifier
    {
        private String patternString;
        private Pattern pattern;

        Classifier(String pattern)
        {
            this.patternString = pattern;
            this.pattern = Pattern.compile(pattern.replace("*", ".*"));
        }

        @Override
        public int hashCode()
        {
            return patternString.hashCode();
        }

        @Override
        public boolean equals(Object other)
        {
            if ( ! (other instanceof Classifier) ) return false;
            return patternString.equals(other.toString());
        }

        @Override
        public String toString()
        {
            return patternString;
        }
    }


    /**
     * Matcher that returns true for artifacts that matched a certain list of patterns.
     * Has a <code>matches</code> methods instead of <code>include</code> for better readability.
     *
     */
    static final private class EaseStrictPatternArtifactMatcher extends AbstractStrictPatternArtifactFilter
    {
        /**
         * Creates a new filter that includes artifacts that match the specified patterns.
         * 
         * @param patterns
         *            the list of artifact patterns to match, as described above
         */
        public EaseStrictPatternArtifactMatcher( List<String> patterns )
        {
            super( patterns, true );
        }

        /**
         * Check if an artifact matches the filter patterns.
         * @param artifact
         * 			Artifact to check against the filter patterns.
         * @return	true, if the artifact matched against any of the filter patterns.
         */
        public boolean matches(Artifact artifact)
        {
            return super.include(artifact);
        }
    }

}
