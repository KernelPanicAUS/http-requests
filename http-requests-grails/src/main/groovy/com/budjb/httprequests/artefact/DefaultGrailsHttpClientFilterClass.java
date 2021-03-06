/*
 * Copyright 2016 Bud Byrd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.budjb.httprequests.artefact;

import org.grails.core.AbstractInjectableGrailsClass;

public class DefaultGrailsHttpClientFilterClass extends AbstractInjectableGrailsClass implements GrailsHttpClientFilterClass {
    public DefaultGrailsHttpClientFilterClass(Class clazz) {
        super(clazz, HttpClientFilterArtefactHandler.SUFFIX);
    }

    public DefaultGrailsHttpClientFilterClass(Class clazz, String trailingName) {
        super(clazz, trailingName);
    }
}
