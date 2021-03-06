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
package com.budjb.httprequests

import com.budjb.httprequests.exception.HttpInternalServerErrorException
import com.budjb.httprequests.filter.HttpClientRetryFilter
import com.budjb.httprequests.filter.bundled.BasicAuthFilter
import com.budjb.httprequests.filter.bundled.GZIPFilter
import com.budjb.httprequests.filter.bundled.HttpStatusExceptionFilter
import com.budjb.httprequests.filter.bundled.Slf4jLoggingFilter
import groovy.util.slurpersupport.GPathResult
import spock.lang.Ignore
import spock.lang.Unroll

import java.util.zip.GZIPInputStream

@Ignore
abstract class HttpIntegrationTestSuiteSpec extends AbstractIntegrationSpec {
    def 'When a GET request is made to /testBasicGet, the proper response is received'() {
        when:
        def response = httpClientFactory.createHttpClient().get(new HttpRequest().setUri("${baseUrl}/testBasicGet"))

        then:
        response.getEntity(String) == 'The quick brown fox jumps over the lazy dog.'
    }

    def 'When a DELETE request is made to /testBasicDelete, the proper response is received'() {
        when:
        def response = httpClientFactory.createHttpClient().delete(new HttpRequest().setUri("${baseUrl}/testBasicDelete"))

        then:
        response.getEntity(String) == "Please don't hurt me!"
    }

    def 'When a POST request is made to /testBasicPost, the proper response is received'() {
        when:
        def response = httpClientFactory.createHttpClient().post(
            new HttpRequest().setUri("${baseUrl}/testBasicPost").setContentType('text/plain'),
            "Please don't play the repeating game!"
        )

        then:
        response.getEntity(String) == "Please don't play the repeating game!"
    }

    def 'When a PUT request is made to /testBasicPut, the proper response is received'() {
        when:
        def response = httpClientFactory.createHttpClient().put(
            new HttpRequest().setUri("${baseUrl}/testBasicPut").setContentType('text/plain'),
            "Please don't play the repeating game!"
        )

        then:
        response.getEntity(String) == "Please don't play the repeating game!"
    }

    def 'When an Accept header is assigned, the server receives and processes it correctly'() {
        when:
        def response = httpClientFactory.createHttpClient().get(new HttpRequest()
            .setUri("${baseUrl}/testAccept")
            .setAccept('text/plain')
        )

        then:
        response.getEntity(String) == 'I am plain text.'
    }

    def 'When an unknown Accept header is assigned, the server receives it and returns an error'() {
        when:
        def response = httpClientFactory.createHttpClient().get(new HttpRequest()
            .setUri("${baseUrl}/testAccept")
            .setAccept('foo/bar')
        )

        then:
        response.status == 406
    }

    def 'When a read timeout is reached, a SocketTimeoutException occurs'() {
        when:
        httpClientFactory.createHttpClient().get(new HttpRequest()
            .setUri("${baseUrl}/testReadTimeout")
            .setReadTimeout(1000)
        )

        then:
        thrown SocketTimeoutException
    }

    def 'When a call to /testBasicGet is made, the proper byte stream is received'() {
        when:
        def response = httpClientFactory.createHttpClient().get(new HttpRequest().setUri("${baseUrl}/testBasicGet"))

        then:
        response.getEntity(byte[]) == [84, 104, 101, 32, 113, 117, 105, 99, 107, 32, 98, 114, 111, 119, 110, 32, 102, 111, 120, 32, 106, 117, 109, 112, 115, 32, 111, 118, 101, 114, 32, 116, 104, 101, 32, 108, 97, 122, 121, 32, 100, 111, 103, 46] as byte[]
    }

    def 'When a redirect is received and the client is configured to follow it, the proper response is received'() {
        when:
        def response = httpClientFactory.createHttpClient().get(new HttpRequest().setUri("${baseUrl}/testRedirect"))

        then:

        response.getEntity(String) == 'The quick brown fox jumps over the lazy dog.'
    }

    def 'When a redirect is received and the client is configured to not follow it, the response status code is 302'() {
        when:
        def response = httpClientFactory.createHttpClient().get(new HttpRequest()
            .setUri("${baseUrl}/testRedirect")
            .setFollowRedirects(false)
        )

        then:
        response.status == 302
    }

    def 'When a request includes headers, the server receives them correctly'() {
        when:
        def response = httpClientFactory.createHttpClient().get(new HttpRequest()
            .setUri("${baseUrl}/testHeaders")
            .addHeaders([foo: ['bar'], key: ['value']])
        )

        then:
        def json = response.getEntity(Map)
        json.foo == ['bar']
        json.key == ['value']
    }

    def 'When a request includes headers with multiple values, the server receives them correctly'() {
        setup:
        def request = new HttpRequest()
            .setUri("${baseUrl}/testHeaders")
            .addHeader('foo', 'bar')
            .addHeader('foo', 'baz')
            .addHeader('hi', 'there')

        when:
        def response = httpClientFactory.createHttpClient().get(request)

        then:
        def json = response.getEntity(Map)
        json.foo == ['bar,baz'] || json.foo == ['bar', 'baz']
        json.hi == ['there']
    }

    def 'When a request includes query parameters, the server receives them correctly'() {
        setup:
        def request = new HttpRequest()
            .setUri("${baseUrl}/testParams")
            .addQueryParameter('foo', 'bar')
            .addQueryParameter('key', 'value')

        when:
        def response = httpClientFactory.createHttpClient().get(request)

        then:
        response.getEntity(Map) == [foo: ['bar'], key: ['value']]
    }

    def 'When a request includes query parameters with multiple values, the server receives them correctly'() {
        setup:
        def request = new HttpRequest()
            .setUri("${baseUrl}/testParams")
            .addQueryParameter('foo', 'bar')
            .addQueryParameter('foo', 'baz')
            .addQueryParameter('hi', 'there')

        when:
        def response = httpClientFactory.createHttpClient().get(request)

        then:
        response.getEntity(Map) == ['foo': ['bar', 'baz'], 'hi': ['there']]
    }

    def 'When a response has a status of 500, an HttpInternalServerErrorException is thrown'() {
        when:
        httpClientFactory.createHttpClient()
            .addFilter(new HttpStatusExceptionFilter())
            .get(new HttpRequest().setUri("${baseUrl}/test500"))

        then:
        thrown HttpInternalServerErrorException
    }

    def 'When a response has a status of 500 but the client is configured to not throw exceptions, no exception is thrown'() {
        when:
        def response = httpClientFactory.createHttpClient().get(new HttpRequest()
            .setUri("${baseUrl}/test500")
        )

        then:
        notThrown HttpInternalServerErrorException
        response.status == 500
    }

    def 'When the client sends form data as the request entity, the server receives them correctly'() {
        setup:
        FormData formData = new FormData()
        formData.addField('foo', 'bar')
        formData.addField('key', 'value')


        when:
        def response = httpClientFactory.createHttpClient().post(new HttpRequest().setUri("${baseUrl}/testForm"), formData)

        then:
        response.getEntity(Map) == ['foo': ['bar'], 'key': ['value']]
    }

    def 'When the client sends form data with multiple values as the request entity, the server receives them correctly'() {
        setup:
        FormData formData = new FormData()
        formData.addField('foo', 'bar')
        formData.addField('foo', 'baz')
        formData.addField('key', 'value')


        when:
        def response = httpClientFactory.createHttpClient().post(new HttpRequest().setUri("${baseUrl}/testForm"), formData)

        then:
        response.getEntity(Map) == ['foo': ['bar', 'baz'], 'key': ['value']]
    }

    def 'When a server requires basic authentication but none is provided, an HttpUnauthorizedException is thrown'() {
        when:
        def response = httpClientFactory.createHttpClient().get(new HttpRequest().setUri("${baseUrl}/testAuth"))

        then:
        response.status == 401
    }

    def 'When a server requires basic authentication and the client provides it, the proper response is received'() {
        when:
        def response = httpClientFactory
            .createHttpClient()
            .addFilter(new BasicAuthFilter('foo', 'bar'))
            .get(new HttpRequest().setUri("${baseUrl}/testAuth"))

        then:
        response.getEntity(String) == 'welcome'
    }

    def 'If a retry filter requests a retry, ensure its proper operations'() {
        setup:
        boolean ran = false

        HttpClientRetryFilter filter = new HttpClientRetryFilter() {
            @Override
            boolean onRetry(HttpContext context) {
                if (context.getRetries() == 0) {
                    ran = true
                    return true
                }
                return false
            }
        }

        when:
        httpClientFactory.createHttpClient().addFilter(filter).get(
            new HttpRequest().setUri("${baseUrl}/testBasicGet")
        )

        then:
        ran
    }

    def 'Validate builder form of GET works'() {
        when:
        def response = httpClientFactory.createHttpClient().get {
            uri = "${baseUrl}/testBasicGet"
        }

        then:
        response.getEntity(String) == 'The quick brown fox jumps over the lazy dog.'
    }

    def 'Validate builder form of DELETE works'() {
        when:
        def response = httpClientFactory.createHttpClient().delete {
            uri = "${baseUrl}/testBasicDelete"
        }

        then:
        response.getEntity(String) == 'Please don\'t hurt me!'
    }

    /*
    def 'Validate builder form of TRACE works'() {
        when:
        def response = httpClientFactory.createHttpClient().trace {
            uri = "${baseUrl}/testBasicTrace"
            headers = [foo: 'bar']
            logConversation = true
        }

        then:
        response.entityAsJson == [foo: ['bar']]
    }
    */

    def 'Validate builder form of HEAD works'() {
        when:
        def response = httpClientFactory.createHttpClient().head { uri = "${baseUrl}/testBasicHead" }

        then:
        response.headers.foo.toSet() == ['baz', 'bar'].toSet()
        response.headers.hi == ['there']
        !response.hasEntity()
    }

    def 'Validate builder form of POST with no entity works'() {
        when:
        def response = httpClientFactory.createHttpClient().post { uri = "${baseUrl}/testBasicPost" }

        then:
        !response.hasEntity()
    }

    def 'Validate builder form of POST with a byte array entity works'() {
        when:
        def response = httpClientFactory.createHttpClient().post('Hello'.getBytes()) {
            uri = "${baseUrl}/testBasicPost"
        }

        then:
        response.getEntity(String) == 'Hello'
    }

    def 'Validate builder form of POST with a string entity works'() {
        when:
        def response = httpClientFactory.createHttpClient().post('Hello') { uri = "${baseUrl}/testBasicPost" }

        then:
        response.getEntity(String) == 'Hello'
    }

    def 'Validate builder form of POST with an input stream works'() {
        setup:
        def stream = new ByteArrayInputStream('Hello'.getBytes())

        when:
        def response = httpClientFactory.createHttpClient().post(stream) { uri = "${baseUrl}/testBasicPost" }

        then:
        response.getEntity(String) == 'Hello'
    }

    def 'Validate that a request can be made with a Map'() {
        when:
        def response = httpClientFactory.createHttpClient().post([foo: ['bar', 'baz']]) {
            uri = "${baseUrl}/testBasicPost"
        }

        then:
        response.getEntity(Map) == [foo: ['bar', 'baz']]
        response.getEntity(String) == '{"foo":["bar","baz"]}'
    }

    def 'Validate that a request can be made with a GString'() {
        setup:
        String val = "friend"

        when:
        def response = httpClientFactory.createHttpClient().post("Hello, ${val}!") {
            uri = "${baseUrl}/testBasicPost"
        }

        then:
        response.getEntity(String) == 'Hello, friend!'
    }

    def 'Validate builder form of POST with FormData works'() {
        setup:
        def form = new FormData()
        form.addField('foo', 'bar')
        form.addField('foo', 'baz')
        form.addField('hi', 'there')

        when:
        def response = httpClientFactory.createHttpClient().post(form) { uri = "${baseUrl}/testBasicPost" }

        then:
        response.getEntity(String).contains('foo=bar')
        response.getEntity(String).contains('foo=baz')
        response.getEntity(String).contains('hi=there')
    }

    def 'Validate builder form of PUT with no entity works'() {
        when:
        def response = httpClientFactory.createHttpClient().put { uri = "${baseUrl}/testBasicPut" }

        then:
        !response.hasEntity()
    }

    def 'Validate builder form of PUT with a byte array entity works'() {
        when:
        def response = httpClientFactory.createHttpClient().put('Hello'.getBytes()) { uri = "${baseUrl}/testBasicPut" }

        then:
        response.getEntity(String) == 'Hello'
    }

    def 'Validate builder form of PUT with a string entity works'() {
        when:
        def response = httpClientFactory.createHttpClient().put('Hello') { uri = "${baseUrl}/testBasicPut" }

        then:
        response.getEntity(String) == 'Hello'
    }

    def 'Validate builder form of PUT with an input stream works'() {
        setup:
        def stream = new ByteArrayInputStream('Hello'.getBytes())

        when:
        def response = httpClientFactory.createHttpClient().put(stream) { uri = "${baseUrl}/testBasicPut" }

        then:
        response.getEntity(String) == 'Hello'
    }

    /*
    def 'Validate builder form of PUT with FormData works'() {
        setup:
        def form = new FormData()
        form.addField('foo', 'bar')
        form.addField('foo', 'baz')
        form.addField('hi', 'there')

        when:
        def response = httpClientFactory.createHttpClient().put(form) { uri = "${baseUrl}/testBasicPut" }

        then:
        response.entityAsString == 'foo=bar&foo=baz&hi=there'
    }
    */

    def 'Validate request form of POST with no entity works'() {
        setup:
        def request = new HttpRequest("${baseUrl}/testBasicPost")

        when:
        def response = httpClientFactory.createHttpClient().post(request)

        then:
        !response.hasEntity()
    }

    def 'Validate request form of POST with a byte array entity works'() {
        setup:
        def request = new HttpRequest("${baseUrl}/testBasicPost")

        when:
        def response = httpClientFactory.createHttpClient().post(request, 'Hello'.getBytes())

        then:
        response.getEntity(String) == 'Hello'
    }

    def 'Validate request form of POST with a string entity works'() {
        setup:
        def request = new HttpRequest("${baseUrl}/testBasicPost")

        when:
        def response = httpClientFactory.createHttpClient().post(request, 'Hello')

        then:
        response.getEntity(String) == 'Hello'
    }

    def 'Validate request form of POST with an input stream works'() {
        setup:
        def request = new HttpRequest("${baseUrl}/testBasicPost")
        def stream = new ByteArrayInputStream('Hello'.getBytes())

        when:
        def response = httpClientFactory.createHttpClient().post(request, stream)

        then:
        response.getEntity(String) == 'Hello'
    }

    def 'Validate request form of POST with FormData works'() {
        setup:
        def form = new FormData()
        form.addField('foo', 'bar')
        form.addField('foo', 'baz')
        form.addField('hi', 'there')

        def request = new HttpRequest("${baseUrl}/testBasicPost")

        when:
        def response = httpClientFactory.createHttpClient().post(request, form)

        then:
        response.getEntity(String).contains('foo=bar')
        response.getEntity(String).contains('foo=baz')
        response.getEntity(String).contains('hi=there')
    }

    def 'Validate request form of PUT with no entity works'() {
        setup:
        def request = new HttpRequest("${baseUrl}/testBasicPut")

        when:
        def response = httpClientFactory.createHttpClient().put(request)

        then:
        !response.hasEntity()
    }

    def 'Validate request form of PUT with a byte array entity works'() {
        setup:
        def request = new HttpRequest("${baseUrl}/testBasicPut")

        when:
        def response = httpClientFactory.createHttpClient().put(request, 'Hello'.getBytes())

        then:
        response.getEntity(String) == 'Hello'
    }

    def 'Validate request form of PUT with a string entity works'() {
        setup:
        def request = new HttpRequest("${baseUrl}/testBasicPut")

        when:
        def response = httpClientFactory.createHttpClient().put(request, 'Hello')

        then:
        response.getEntity(String) == 'Hello'
    }

    def 'Validate request form of PUT with an input stream works'() {
        setup:
        def stream = new ByteArrayInputStream('Hello'.getBytes())
        def request = new HttpRequest("${baseUrl}/testBasicPut")

        when:
        def response = httpClientFactory.createHttpClient().put(request, stream)

        then:
        response.getEntity(String) == 'Hello'
    }

    /*
    def 'Validate request form of PUT with FormData works'() {
        setup:
        def form = new FormData()
        form.addField('foo', 'bar')
        form.addField('foo', 'baz')
        form.addField('hi', 'there')

        def request = new HttpRequest("${baseUrl}/testBasicPut")

        when:
        def response = httpClientFactory.createHttpClient().put(request, form)

        then:
        response.entityAsString == 'foo=bar&foo=baz&hi=there'
    }
    */

    def 'When a content type is already set, it will not be overwritten by a converter'() {
        when:
        def response = httpClientFactory.createHttpClient().post('hi!') {
            uri = "${baseUrl}/printContentType"
            contentType = 'foo/bar'
        }

        then:
        response.getEntity(String).startsWith('foo/bar')
    }

    def 'When no content type is set, it will be set by the converter'() {
        when:
        def response = httpClientFactory.createHttpClient().post('hi!') {
            uri = "${baseUrl}/printContentType"
        }

        then:
        response.getEntity(String).startsWith('text/plain')
    }

    def 'When the response is not buffered, the entity can only be retrieved once'() {
        setup:
        def response = httpClientFactory.createHttpClient().post([foo: ['bar', 'baz']]) {
            uri = "${baseUrl}/testBasicPost"
            bufferResponseEntity = false
        }
        response.getEntity(Map) == [foo: ['bar', 'baz']]

        when:
        response.getEntity(String)

        then:
        thrown IOException
    }

    def 'When the response is buffered, the entity can be retrieved multiple times'() {
        setup:
        def response = httpClientFactory.createHttpClient().post([foo: ['bar', 'baz']]) {
            uri = "${baseUrl}/testBasicPost"
        }
        response.getEntity(Map) == [foo: ['bar', 'baz']]

        expect:
        response.getEntity(String) == '{"foo":["bar","baz"]}'
    }

    def 'When a GZIPFilter is applied to the request, the request is compressed'() {
        setup:
        httpClientFactory.addFilter(new GZIPFilter())
        def request = HttpRequest.build {
            uri = "${baseUrl}/echo"
        }

        when:
        def response = httpClientFactory.createHttpClient().post(request, 'Hello, world!')

        then:
        StreamUtils.readString(new GZIPInputStream(response.getEntity()), 'UTF-8') == 'Hello, world!'
    }

    def 'Ensure the LoggingFilter does not cause interruptions to HTTP requests.'() {
        when:
        def response = httpClientFactory.createHttpClient().post("Hello, world") {
            uri = "${baseUrl}/testBasicPost"
        }

        then:
        notThrown IOException
        response.getEntity(String) == 'Hello, world'

        when:
        response = httpClientFactory.createHttpClient().get {
            uri = "${baseUrl}/testBasicGet"
            accept = "text/plain"
        }

        then:
        notThrown IOException
        response.getEntity(String) == 'The quick brown fox jumps over the lazy dog.'
    }

    @Unroll
    def 'When an accepted character set #charset is requested, the proper output is received and parsed'() {
        setup:
        String input = 'åäö'

        when:
        def response = httpClientFactory.createHttpClient().addFilter(new Slf4jLoggingFilter()).post(input) {
            uri = "${baseUrl}/acceptContentType"
            accept = "text/plain;charset=${charset}"
        }

        then:
        response.contentType == 'text/plain'
        response.charset == charset
        response.getEntity(String) == output

        where:
        charset  | output
        'euc-jp' | '奪辰旦'
        'UTF-8'  | 'åäö'
    }

    @Unroll
    def 'When #writeType is sent, the entity was properly written and received'() {
        when:
        def response = httpClientFactory.createHttpClient().post(input) {
            uri = "${baseUrl}/echo"
        }

        then:
        response.getEntity(String) == output
        response.contentType == contentType

        when:
        def read = response.getEntity(readType)

        then:
        read != null
        readType.isInstance(read)
        notThrown(Exception)

        where:
        writeType  | input                                  | output            | readType    | contentType
        'Map'      | [foo: ['bar']]                         | '{"foo":["bar"]}' | Map         | 'application/json'
        'List'     | ['foo', 'bar']                         | '["foo","bar"]'   | List        | 'application/json'
        'String'   | "Hello, world!"                        | "Hello, world!"   | String      | 'text/plain'
        'byte[]'   | [102, 111, 111, 98, 97, 114] as byte[] | 'foobar'          | byte[]      | 'application/octet-stream'
        'GString'  | "${'foo'}bar"                          | 'foobar'          | String      | 'text/plain'
        'FormData' | {
            def form = new FormData(); form.addField('foo', 'bar'); return form
        }.call()                                            | 'foo=bar'         | String      | 'application/x-www-form-urlencoded'
        'XML'      | '<Foo>bar</Foo>'                       | '<Foo>bar</Foo>'  | GPathResult | 'text/plain'
    }

    def 'Closing the response multiple times has no effect and does not cause an error'() {
        setup:
        def response = httpClientFactory.createHttpClient().get { uri = "${baseUrl}/testBasicGet" }

        when:
        response.close()
        response.close()
        response.close()

        then:
        notThrown Exception
    }

    def 'When a request is retried, the entity is resent correctly'() {
        setup:
        InputStream inputStream = new ByteArrayInputStream('test payload'.bytes)
        HttpClientRetryFilter retryFilter = new HttpClientRetryFilter() {
            @Override
            boolean onRetry(HttpContext context) {
                return context.retries == 0
            }
        }

        when:
        def response = httpClientFactory.createHttpClient().addFilter(retryFilter).post(inputStream) {
            uri = "${baseUrl}/testBasicPost"
        }

        then:
        response.status == 200
        response.hasEntity()
        response.getEntity(String) == 'test payload'
    }
}
