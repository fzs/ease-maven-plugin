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

import java.io.File;
import java.io.IOException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.FileUtils;

class EaseHelper
{
    static Artifact createArtifact( String groupId, String artifactId,
            String version, String type, String classifier,
            ArtifactFactory artifactFactory )
    {
        return artifactFactory.createArtifactWithClassifier( groupId,
                artifactId, version, type, classifier );
    }


    static Artifact createArtifact( String coords,
            ArtifactFactory artifactFactory )
            throws MojoExecutionException
    {
        String[] strings = coords.split( ":" );
        if ( strings.length < 4 || strings.length > 5 )
        {
            throw new MojoExecutionException( "Can not parse coordinates: "
                                              + coords );
        }
        String groupId = strings[0];
        String artifactId = strings[1];
        String type = strings[2];
        String version = null;
        String classifier = null;
        if ( strings.length == 5 )
        {
            version = strings[4];
            classifier = strings[3];
        }
        else if ( strings.length == 4 )
        {
            version = strings[3];
        }
        return createArtifact( groupId, artifactId, version, type, classifier,
                artifactFactory );
    }


    static void writeAndAttachArtifactList( StringBuilder builder,
            MavenProject project, MavenProjectHelper projectHelper, Log log )
            throws MojoExecutionException
    {
        String buildDir = project.getBuild()
                .getDirectory();
        String destFile = buildDir + File.separator + project.getArtifactId()
                          + "-" + project.getVersion() + "-artifacts.txt";
        try
        {
            if ( FileUtils.fileExists( destFile ) )
            {
                FileUtils.fileDelete( destFile );
            }
            if ( !FileUtils.fileExists( buildDir ) )
            {
                FileUtils.mkdir( buildDir );
            }
            FileUtils.fileWrite( destFile, "UTF-8", builder.toString() );
        }
        catch ( IOException ioe )
        {
            throw new MojoExecutionException( "Could not write artifact list.",
                    ioe );
        }
        projectHelper.attachArtifact( project, "txt", "artifacts",
                FileUtils.getFile( destFile ) );
        log.info( "Successfully attached artifact list to the project." );
    }
}
