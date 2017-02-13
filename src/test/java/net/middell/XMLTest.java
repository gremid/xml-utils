/*
 * Copyright © 2017 Gregor Middell (http://gregor.middell.net/)
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
package net.middell;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathExpression;

import static java.util.Collections.singletonMap;
import static net.middell.XML.children;
import static net.middell.XML.elements;
import static net.middell.XML.namespaceContext;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="http://gregor.middell.net/">Gregor Middell</a>
 */
public class XMLTest {

    @Test
    public void factories() throws Exception {
        try (InputStream sample = sample()) {
            assertNotNull(XML.newDocumentBuilder().parse(sample));
        }

        try (InputStream sample = sample()) {
            XML.newTransformer().transform(new StreamSource(sample), new StreamResult(NULL_WRITER));
        }

        try (InputStream sample = sample()) {
            final XMLStreamReader xmlStreamReader = XML.inputFactory().createXMLStreamReader(sample);
            try {
                while (xmlStreamReader.hasNext()) {
                    xmlStreamReader.next();
                }
            } finally {
                xmlStreamReader.close();
            }
        }
    }

    @Test
    public void traversal() {
        final Document sample = sampleDocument();
        
        assertThat(elements(children(sample)), not(empty()));
        assertThat(children(sample.getDocumentElement()), not(empty()));
    }
    
    @Test
    public void xpath() {
        final NamespaceContext nsCtx = namespaceContext(singletonMap(
                "sample", "http://example.com/ns/1.0"
        ));

        assertThat(sampleXPath(XML.xpath("//root")), is(empty()));
        assertThat(sampleXPath(XML.xpath("//sample:root", nsCtx)), is(not(empty())));
    }

    @Test
    public void xmlToString() throws Exception {
        assertThat(XML.toString(sampleDocument()), startsWith("<root"));
    }

    private static Iterable<Node> sampleXPath(XPathExpression xp) {
        return XML.nodes(xp, sampleDocument());
    }

    private static Document sampleDocument() {
        try (InputStream sample = sample()) {
            return XML.newDocumentBuilder().parse(sample);
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static InputStream sample() {
        return XMLTest.class.getResourceAsStream("/sample.xml");
    }

    private static <T> TypeSafeMatcher<Iterable<T>> empty() {
        return new TypeSafeMatcher<Iterable<T>>() {
            @Override
            protected boolean matchesSafely(Iterable<T> item) {
                for (T ignored : item) {
                    return false;
                }

                return true;
            }

            @Override
            protected void describeMismatchSafely(Iterable<T> item, Description description) {
                final List<T> itemList = new ArrayList<T>();
                item.forEach(itemList::add);

                description.appendText("was ").appendValue(itemList);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("[]");
            }
        };
    }

    private static final Writer NULL_WRITER = new Writer() {
        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            // no-op
        }

        @Override
        public void flush() throws IOException {
            // no-op
        }

        @Override
        public void close() throws IOException {
            // no-op
        }
    };
}
