/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Yegor Bugayenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.jpeek;

import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.jcabi.xml.XSL;
import com.jcabi.xml.XSLDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.cactoos.collection.Mapped;
import org.cactoos.io.LengthOf;
import org.cactoos.io.ResourceOf;
import org.cactoos.io.TeeInput;
import org.cactoos.list.ListOf;
import org.cactoos.scalar.And;
import org.cactoos.scalar.IoCheckedScalar;
import org.jpeek.metrics.cohesion.CAMC;
import org.jpeek.metrics.cohesion.LCOM;
import org.jpeek.metrics.cohesion.LCOM2;
import org.jpeek.metrics.cohesion.LCOM3;
import org.jpeek.metrics.cohesion.NHD;
import org.jpeek.metrics.cohesion.OCC;
import org.xembly.Directives;
import org.xembly.Xembler;

/**
 * Application.
 *
 * <p>There is no thread-safety guarantee.
 *
 * @author Yegor Bugayenko (yegor256@gmail.com)
 * @version $Id$
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public final class App {

    /**
     * Index XSL stylesheet.
     */
    private static final XSL STYLESHEET = XSLDocument.make(
        App.class.getResourceAsStream("index.xsl")
    );

    /**
     * Matrix XSL stylesheet.
     */
    private static final XSL MATRIX = XSLDocument.make(
        App.class.getResourceAsStream("matrix.xsl")
    );

    /**
     * XSL stylesheet.
     */
    private static final XSL BADGE = XSLDocument.make(
        App.class.getResourceAsStream("badge.xsl")
    );

    /**
     * Location of the project to analyze.
     */
    private final Path input;

    /**
     * Directory to save reports to.
     */
    private final Path output;

    /**
     * Ctor.
     * @param source Source directory
     * @param target Target dir
     */
    public App(final Path source, final Path target) {
        this.input = source;
        this.output = target;
    }

    /**
     * Analyze sources.
     * @throws IOException If fails
     */
    @SuppressWarnings("PMD.ExcessiveMethodLength")
    public void analyze() throws IOException {
        if (Files.exists(this.output)) {
            throw new IllegalStateException(
                String.format(
                    "Directory/file already exists: %s",
                    this.output.normalize().toAbsolutePath()
                )
            );
        }
        final Base base = new DefaultBase(this.input);
        final Iterable<Metric> metrics = new ListOf<>(
            new CAMC(base),
            new LCOM(base),
            new OCC(base),
            new NHD(base),
            new LCOM2(base),
            new LCOM3(base)
        );
        new IoCheckedScalar<>(
            new And(
                metrics,
                metric -> {
                    new Report(metric).save(this.output);
                }
            )
        ).value();
        XML index = new XMLDocument(
            new Xembler(
                new Index(this.output).value()
            ).xmlQuietly()
        );
        final double score = new Avg(
            new Mapped<>(
                index.xpath("//metric/score/text()"),
                Double::parseDouble
            )
        ).value();
        index = new XMLDocument(
            new Xembler(
                new Directives().xpath("/metrics").attr("score", score)
            ).applyQuietly(index.node())
        );
        new LengthOf(
            new TeeInput(
                index.toString(),
                this.output.resolve("index.xml")
            )
        ).value();
        new LengthOf(
            new TeeInput(
                App.STYLESHEET.transform(index).toString(),
                this.output.resolve("index.html")
            )
        ).value();
        final XML matrix = new XMLDocument(
            new Xembler(
                new Matrix(this.output).value()
            ).xmlQuietly()
        );
        new LengthOf(
            new TeeInput(
                matrix.toString(),
                this.output.resolve("matrix.xml")
            )
        ).value();
        new LengthOf(
            new TeeInput(
                App.MATRIX.transform(matrix).toString(),
                this.output.resolve("matrix.html")
            )
        ).value();
        new LengthOf(
            new TeeInput(
                App.BADGE.transform(
                    new XMLDocument(
                        new Xembler(
                            new Directives().add("badge").set(
                                String.format("%.4f", score)
                            ).attr("style", "round")
                        ).xmlQuietly()
                    )
                ).toString(),
                this.output.resolve("badge.svg")
            )
        ).value();
        new IoCheckedScalar<>(
            new And(
                new ListOf<>("index", "matrix", "jpeek"),
                this::copy
            )
        ).value();
    }

    /**
     * Copy resource.
     * @param name The name of resource
     * @throws IOException If fails
     */
    private void copy(final String name) throws IOException {
        final String file = String.format("%s.xsl", name);
        new LengthOf(
            new TeeInput(
                new ResourceOf(String.format("org/jpeek/%s", file)),
                this.output.resolve(file)
            )
        ).value();
    }

}
