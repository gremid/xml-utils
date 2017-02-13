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

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * @author <a href="http://gregor.middell.net/">Gregor Middell</a>
 */
public class XML {

    private static DocumentBuilderFactory documentBuilderFactory;
    private static TransformerFactory transformerFactory;
    private static XMLInputFactory inputFactory;
    private static XPathFactory xpathFactory;

    public static DocumentBuilder newDocumentBuilder() {
        try {
            if (documentBuilderFactory == null) {
                documentBuilderFactory = DocumentBuilderFactory.newInstance();
                documentBuilderFactory.setNamespaceAware(true);
                documentBuilderFactory.setXIncludeAware(true);
            }
            return documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static XMLInputFactory inputFactory() {
        if (inputFactory == null) {
            inputFactory = XMLInputFactory.newFactory();
        }
        return inputFactory;
    }

    public static TransformerFactory transformerFactory() {
        if (transformerFactory == null) {
            transformerFactory = TransformerFactory.newInstance();
        }
        return transformerFactory;
    }

    public static Transformer newTransformer() {
        try {
            return transformerFactory().newTransformer();
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Transformer indentingTransformer(Transformer transformer) {
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        return transformer;
    }

    public static String toString(Node node) {
        try {
            final StringWriter stringWriter = new StringWriter();
            final Transformer transformer = indentingTransformer(newTransformer());
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(new DOMSource(node), new StreamResult(stringWriter));
            return stringWriter.toString();
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    public static XPathExpression xpath(String expression) {
        return xpath(expression, null);
    }

    public static XPathExpression xpath(String expression, NamespaceContext nsCtx) {
        try {
            if (xpathFactory == null) {
                xpathFactory = XPathFactory.newInstance();
            }
            final XPath xp = xpathFactory.newXPath();
            Optional.ofNullable(nsCtx).ifPresent(xp::setNamespaceContext);
            return xp.compile(expression);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    public static Iterable<Node> nodes(final NodeList list) {
        final int nl = list.getLength();
        return () -> new Iterator<Node>() {

            private int nc = 0;

            @Override
            public boolean hasNext() {
                return (nc < nl);
            }

            @Override
            public Node next() {
                return list.item(nc++);
            }

        };
    }

    public static Iterable<Node> nodes(XPathExpression xp, Object item) {
        try {
            return nodes((NodeList) xp.evaluate(item, XPathConstants.NODESET));
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }
    
    public static Iterable<Node> children(Node parent) {
        return nodes(parent.getChildNodes());
    }

    public static <T> Iterable<T> nodesOfType(Iterable<Node> nodes, short nodeType, Class<T> type) {
        return map(filter(nodes, n -> n.getNodeType() == nodeType), type::cast);
    }

    public static Iterable<Element> elements(Iterable<Node> nodes) {
        return nodesOfType(nodes, Node.ELEMENT_NODE, Element.class);
    }

    public static Iterable<Element> elements(Element parent, String tagName) {
        return elements(nodes(parent.getElementsByTagName(tagName)));
    }

    public static NamespaceContext namespaceContext(Map<String, String> mappings) {
        final Map<String, String> forward = new HashMap<>(mappings);
        final Map<String, String> reverse = new HashMap<>();

        forward.put(XMLConstants.XML_NS_PREFIX, XMLConstants.XML_NS_URI);
        forward.put(XMLConstants.XMLNS_ATTRIBUTE, XMLConstants.XMLNS_ATTRIBUTE_NS_URI);

        forward.forEach((prefix, uri) -> reverse.put(uri, prefix));

        return new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return forward.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String nsUri) {
                return reverse.get(nsUri);
            }

            @Override
            public Iterator getPrefixes(String nsUri) {
                final String prefix = getPrefix(nsUri);
                if (prefix == null) {
                    return Collections.emptySet().iterator();
                }
                return Collections.singleton(prefix).iterator();
            }
        };
    }

    public static <S, T> Iterable<T> map(Iterable<S> iterable, Function<S, T> mapping) {
        return () -> {
            final Iterator<S> it = iterable.iterator();
            return new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public T next() {
                    return mapping.apply(it.next());
                }
            };
        };
    }

    public static <T> Iterable<T> filter(Iterable<T> iterable, Predicate<T> filter) {
        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                final Iterator<T> it = iterable.iterator();
                return new Iterator<T>() {

                    private T next = null;

                    @Override
                    public boolean hasNext() {
                        while (it.hasNext()) {
                            final T candidate = it.next();
                            if (filter.test(candidate)) {
                                next = candidate;
                                return true;
                            }
                        }
                        return false;
                    }

                    @Override
                    public T next() {
                        if (next == null) {
                            throw new IllegalStateException();
                        }
                        final T next = this.next;
                        this.next = null;
                        return next;
                    }
                };
            }
        };
    }

}
