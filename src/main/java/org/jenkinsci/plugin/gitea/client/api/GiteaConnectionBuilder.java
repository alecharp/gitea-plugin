/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugin.gitea.client.api;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ServiceLoader;
import jenkins.model.Jenkins;
import org.jenkinsci.plugin.gitea.client.spi.GiteaConnectionFactory;

public final class GiteaConnectionBuilder {

    @NonNull
    private final String serverUrl;

    @NonNull
    private GiteaAuth authentication = new GiteaAuthNone();

    private GiteaConnectionBuilder(@NonNull String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @NonNull
    public static GiteaConnectionBuilder newBuilder(@NonNull String serverUrl) {
        return new GiteaConnectionBuilder(serverUrl);
    }

    @NonNull
    public GiteaConnectionBuilder authentication(@CheckForNull GiteaAuth authentication) {
        this.authentication = authentication == null ? new GiteaAuthNone() : authentication;
        return this;
    }

    @NonNull
    public String serverUrl() {
        return serverUrl;
    }

    @NonNull
    public GiteaAuth authentication() {
        return authentication;
    }

    @NonNull
    public GiteaConnection open() throws IOException {
        // HACK for Jenkins
        // by rights this should be the context classloader, but Jenkins does not expose plugins on that
        // so we need instead to use the uberClassLoader as that will have the implementations
        Jenkins instance = Jenkins.getInstance();
        ClassLoader classLoader =
                instance == null ? getClass().getClassLoader() : instance.getPluginManager().uberClassLoader;
        // END HACK
        ServiceLoader<GiteaConnectionFactory> loader = ServiceLoader.load(GiteaConnectionFactory.class, classLoader);
        long priority = 0L;
        GiteaConnectionFactory best = null;
        for (GiteaConnectionFactory factory : loader) {
            if (factory.canOpen(this)) {
                long p = factory.priority(this);
                if (best == null || p > priority) {
                    best = factory;
                    priority = p;
                }
            }
        }
        if (best != null) {
            return best.open(this);
        }
        throw new IOException("No implementation for connecting to " + serverUrl);
    }
}
