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
package com.budjb.httprequests.converter.bundled

import com.budjb.httprequests.ContentType
import com.budjb.httprequests.converter.EntityWriter

/**
 * An entity writer that will convert a GString.
 */
class GStringEntityWriter implements EntityWriter {
    /**
     * Returns a Content-Type of the converted object that will be set in the HTTP request.
     *
     * If no Content-Type is known, null is returned.
     *
     * @return Content-Type of the converted object, or null if unknown.
     */
    @Override
    ContentType getContentType() {
        return ContentType.TEXT_PLAIN
    }

    /**
     * Determines whether the given class type is supported by the writer.
     *
     * @param type Type to convert.
     * @return Whether the type is supported.
     */
    @Override
    boolean supports(Class<?> type) {
        return GString.isAssignableFrom(type)
    }

    /**
     * Convert the given entity.
     *
     * If an error occurs, null may be returned so that another converter may attempt conversion.
     *
     * @param entity Entity object to convert into a byte array.
     * @param contentType Content-Type of the entity.
     * @return An {@link InputStream} containing the converted entity.
     * @throws Exception when an unexpected error occurs.
     */
    @Override
    InputStream write(Object entity, ContentType contentType) throws Exception {
        String characterSet = contentType?.charset ?: ContentType.DEFAULT_SYSTEM_CHARACTER_SET

        return new ByteArrayInputStream(entity.toString().getBytes(characterSet))
    }
}
