package org.apache.maven.plugin.surefire;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.surefire.log.api.ConsoleLogger;
import org.apache.maven.repository.RepositorySystem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.apache.maven.artifact.versioning.VersionRange.createFromVersion;
import static org.apache.maven.artifact.versioning.VersionRange.createFromVersionSpec;
import static org.apache.maven.plugin.surefire.SurefireDependencyResolver.PROVIDER_GROUP_ID;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.powermock.reflect.Whitebox.invokeMethod;

public class SurefireDependencyResolverTest
{
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldNotBeWithinRangeNullArtifact()
    {
        boolean result = SurefireDependencyResolver.isWithinVersionSpec( null, "[4.7,)" );
        assertThat( result )
                .isFalse();
    }

    @Test
    public void shouldNotBeWithinRange() throws InvalidVersionSpecificationException
    {
        Artifact api = createArtifact( "junit", "junit", "4.6" );
        boolean result = SurefireDependencyResolver.isWithinVersionSpec( api, "[4.7,)" );
        assertThat( result )
                .isFalse();
    }

    @Test
    public void shouldBeWithinRange() throws InvalidVersionSpecificationException
    {
        Artifact api = createArtifact( "junit", "junit", "4.7" );
        boolean result = SurefireDependencyResolver.isWithinVersionSpec( api, "[4.7,)" );
        assertThat( result )
                .isTrue();
    }

    @Test
    public void shouldBeFarWithinRange() throws InvalidVersionSpecificationException
    {
        Artifact api = createArtifact( "junit", "junit", "4.13" );
        boolean result = SurefireDependencyResolver.isWithinVersionSpec( api, "[4.7,)" );
        assertThat( result )
                .isTrue();
    }

    @Test
    public void shouldBeFailWithinRange() throws InvalidVersionSpecificationException
    {
        Artifact api = createArtifact( "junit", "junit", "" );
        expectedException.expect( RuntimeException.class );
        expectedException.expectMessage( "Bug in plugin. Please report with stacktrace" );
        SurefireDependencyResolver.isWithinVersionSpec( api, "[4.7,)" );
    }

    @Test
    public void testResolveArtifact() throws InvalidVersionSpecificationException
    {
        final Artifact provider = createArtifact( "surefire-junit-platform" );
        RepositorySystem repositorySystem = mock( RepositorySystem.class );
        final ArtifactResolutionResult expectedResult = mock( ArtifactResolutionResult.class );
        when( repositorySystem.resolve( any(  ArtifactResolutionRequest.class) ) )
                .then( new Answer<ArtifactResolutionResult>() {
                    @Override
                    public ArtifactResolutionResult answer( InvocationOnMock invocation )
                    {
                        Object[] args = invocation.getArguments();
                        assertThat( args )
                                .hasSize( 1 );
                        ArtifactResolutionRequest request = (ArtifactResolutionRequest) args[0];
                        assertThat( request.getArtifact() )
                                .isSameAs( provider );
                        assertThat( request.isResolveTransitively() )
                                .isTrue();
                        assertThat( request.getArtifactDependencies() )
                                .isNull();
                        assertThat( request.getRemoteRepositories() )
                                .isNull();
                        assertThat( request.getLocalRepository() )
                                .isNull();
                        assertThat( request.getCache() )
                                .isNull();
                        assertThat( request.getCollectionFilter() )
                                .isNull();
                        assertThat( request.getManagedVersionMap() )
                                .isNull();
                        assertThat( request.getMirrors() )
                                .isEmpty();
                        assertThat( request.getProxies() )
                                .isEmpty();
                        assertThat( request.getResolutionFilter() )
                                .isNull();
                        assertThat( request.getServers() )
                                .isEmpty();
                        return expectedResult;
                    }
                } );

        SurefireDependencyResolver surefireDependencyResolver =
                new SurefireDependencyResolver( repositorySystem, null, null, null, null, null );

        ArtifactResolutionResult actualResult = surefireDependencyResolver.resolveArtifact( provider );

        assertThat( actualResult )
                .isSameAs( expectedResult );
    }

    @Test
    public void testGetProviderClasspath() throws Exception
    {
        Artifact api = createArtifact( "surefire-api" );
        api.setFile( new File( "" ) );

        final Artifact provider = createArtifact( "surefire-junit-platform" );
        provider.setFile( new File( "" ) );

        Artifact ext = createArtifact( "org.apiguardian", "apiguardian-api" );
        ext.setFile( new File( "" ) );

        Artifact logger = createArtifact( "surefire-logger-api" );
        logger.setFile( new File( "" ) );

        Set<Artifact> providerArtifacts = new LinkedHashSet<Artifact>();
        providerArtifacts.add( api );
        providerArtifacts.add( provider );
        providerArtifacts.add( ext );
        providerArtifacts.add( logger );

        ArtifactFactory artifactFactory = mock( ArtifactFactory.class );
        VersionRange providerVersion = createFromVersion( "5.3.1" );
        when( artifactFactory.createDependencyArtifact( eq( "org.apache.maven.surefire" ),
                eq( "surefire-junit-platform" ),
                eq( providerVersion ),
                eq( "jar" ),
                isNull( String.class ),
                eq( "test" ) ) )
                .thenReturn( provider );

        final ArtifactResolutionResult result = mock( ArtifactResolutionResult.class );
        when( result.getArtifacts() )
                .thenReturn( providerArtifacts );

        RepositorySystem repositorySystem = mock( RepositorySystem.class );
        when( repositorySystem.resolve( any(  ArtifactResolutionRequest.class) ) )
                .then( new Answer<ArtifactResolutionResult>() {
                    @Override
                    public ArtifactResolutionResult answer( InvocationOnMock invocation )
                    {
                        Object[] args = invocation.getArguments();
                        assertThat( args )
                                .hasSize( 1 );
                        ArtifactResolutionRequest request = (ArtifactResolutionRequest) args[0];
                        assertThat( request.getArtifact() )
                                .isSameAs( provider );
                        assertThat( request.isResolveTransitively() )
                                .isTrue();
                        assertThat( request.getArtifactDependencies() )
                                .isNull();
                        assertThat( request.getRemoteRepositories() )
                                .isNull();
                        assertThat( request.getLocalRepository() )
                                .isNull();
                        assertThat( request.getCache() )
                                .isNull();
                        assertThat( request.getCollectionFilter() )
                                .isNull();
                        assertThat( request.getManagedVersionMap() )
                                .isNull();
                        assertThat( request.getMirrors() )
                                .isEmpty();
                        assertThat( request.getProxies() )
                                .isEmpty();
                        assertThat( request.getResolutionFilter() )
                                .isNull();
                        assertThat( request.getServers() )
                                .isEmpty();
                        return result;
                    }
                } );

        ConsoleLogger log = mock( ConsoleLogger.class );

        SurefireDependencyResolver surefireDependencyResolver =
                new SurefireDependencyResolver( repositorySystem, artifactFactory, log, null, null, null );

        when( log.isDebugEnabled() )
                .thenReturn( true );

        Set<Artifact> classpath = surefireDependencyResolver.getProviderClasspath( "surefire-junit-platform", "5.3.1" );

        assertThat( classpath )
                .hasSize( 4 );

        Iterator<Artifact> it = classpath.iterator();

        assertThat( it.next() )
                .isSameAs( provider );

        assertThat( it.next() )
                .isSameAs( api );

        assertThat( it.next() )
                .isSameAs( logger );

        assertThat( it.next() )
                .isSameAs( ext );
    }

    /*@Test
    public void testAddProviderToClasspath() throws Exception
    {
        Set< Artifact > result = surefireDependencyResolver.addProviderToClasspath( new HashMap< String, Artifact >()
        {{
            put( "String", null );
        }}, null );
        Assert.assertEquals( new HashSet< Artifact >( Arrays.asList( null ) ), result );
    }*/

    @Test
    public void shouldOrderedProviderArtifacts() throws Exception
    {
        Artifact api = createArtifact( "surefire-api" );
        Artifact provider = createArtifact( "surefire-junit-platform" );
        Artifact ext = createArtifact( "org.apiguardian", "apiguardian-api" );
        Artifact logger = createArtifact( "surefire-logger-api" );

        Set<Artifact> providerArtifacts = new LinkedHashSet<Artifact>();
        providerArtifacts.add( api );
        providerArtifacts.add( provider );
        providerArtifacts.add( ext );
        providerArtifacts.add( logger );

        Iterator<Artifact> it = providerArtifacts.iterator();

        assertThat( it.next() )
                .isEqualTo( api );

        assertThat( it.next() )
                .isEqualTo( provider );

        assertThat( it.next() )
                .isEqualTo( ext );

        assertThat( it.next() )
                .isEqualTo( logger );

        Set<Artifact> actual =
                invokeMethod( SurefireDependencyResolver.class, "orderProviderArtifacts", providerArtifacts );

        Set<Artifact> expected = new LinkedHashSet<Artifact>();
        expected.add( provider );
        expected.add( api );
        expected.add( logger );
        expected.add( ext );

        assertThat( actual )
                .isEqualTo( expected );
    }

    private static Artifact createArtifact( String artifactId ) throws InvalidVersionSpecificationException
    {
        return createArtifact( PROVIDER_GROUP_ID, artifactId );
    }


    private static Artifact createArtifact( String groupId, String artifactId )
            throws InvalidVersionSpecificationException
    {
        return createArtifact(groupId, artifactId, "1");
    }

    private static Artifact createArtifact( String groupId, String artifactId, String version )
            throws InvalidVersionSpecificationException
    {
        VersionRange versionSpec = createFromVersionSpec( version );
        return new DefaultArtifact( groupId, artifactId, versionSpec, "compile", "jar", "", null );
    }
}
