/*
 * SPDX-FileCopyrightText: Copyright (c) 2016-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.maven;

import com.yegor256.MayBeSlow;
import com.yegor256.Mktmp;
import com.yegor256.MktmpResolver;
import com.yegor256.WeAreOnline;
import com.yegor256.farea.Farea;
import com.yegor256.tojos.TjSmart;
import java.nio.file.Path;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for {@link RegisterMojo}.
 *
 * @since 0.52
 */
@SuppressWarnings({
    "JTCOP.RuleAllTestsHaveProductionClass",
    "JTCOP.RuleNotContainsTestWord",
    "PMD.AvoidDuplicateLiterals"
})
@ExtendWith({WeAreOnline.class, MktmpResolver.class, MayBeSlow.class})
final class RegisterMojoIT {
    @Test
    void removesOldPulledFiles(@Mktmp final Path temp) throws Exception {
        new Farea(temp).together(
            f -> {
                f.clean();
                f.files().file("src/main/eo/foo.eo").write(
                    String.join(
                        "\n",
                        "+package org.eolang",
                        "",
                        "[] > foo",
                        "  \"Pull\" > @"
                    ).getBytes()
                );
                new EOplugin(f).appendItself();
                f.exec("eo:register", "eo:parse", "eo:shake", "eo:probe", "eo:pull");
                f.files().file("src/main/eo/foo.eo").write(
                    String.join(
                        "\n",
                        "+package org.eolang",
                        "",
                        "[] > foo",
                        "  41 > @"
                    ).getBytes()
                );
                f.exec("eo:register");
                MatcherAssert.assertThat(
                    "Old pulled files must were removed, but it didn't",
                    temp.resolve(
                        String.format(
                            "target/eo/%s",
                            PullMojo.DIR
                        )
                    ).toFile().exists(),
                    Matchers.is(false)
                );
            }
        );
    }

    @Test
    void removesOldResolvedFiles(@Mktmp final Path temp) throws Exception {
        new Farea(temp).together(
            f -> {
                f.clean();
                f.files().file("src/main/eo/foo.eo").write(
                    String.join(
                        "\n",
                        "+package org.eolang",
                        "",
                        "[] > foo",
                        "  \"Resolve\" > @"
                    ).getBytes()
                );
                new EOplugin(f).appendItself();
                f.exec("eo:register", "eo:parse", "eo:shake", "eo:probe", "eo:pull", "eo:resolve");
                f.files().file("src/main/eo/foo.eo").write(
                    String.join(
                        "\n",
                        "+package org.eolang",
                        "",
                        "[] > foo",
                        "  42 > @"
                    ).getBytes()
                );
                f.exec("eo:register");
                MatcherAssert.assertThat(
                    "Old resolved files must were removed, but it didn't",
                    temp.resolve(
                        String.format(
                            "target/eo/%s",
                            ResolveMojo.DIR
                        )
                    ).toFile().exists(),
                    Matchers.is(false)
                );
            }
        );
    }

    @Test
    void removesOldForeignFile(@Mktmp final Path temp) throws Exception {
        new Farea(temp).together(
            f -> {
                f.clean();
                f.files().file("src/main/eo/foo.eo").write(
                    String.join(
                        "\n",
                        "+package org.eolang",
                        "",
                        "# In this program, we refer to the 'String'",
                        "[] > foo",
                        "  \"42\" > @"
                    ).getBytes()
                );
                new EOplugin(f).appendItself();
                f.exec("eo:register", "eo:parse", "eo:shake", "eo:probe", "eo:pull");
                f.files().file("src/main/eo/foo.eo").write(
                    String.join(
                        "\n",
                        "+package org.eolang",
                        "",
                        "# In this program, we refer to the 'Number'.",
                        "[] > foo",
                        "  42 > @"
                    ).getBytes()
                );
                f.exec("eo:register", "eo:parse", "eo:shake", "eo:probe", "eo:pull");
                final TjSmart foreign = RegisterMojoIT.foreign(
                    temp.resolve("target/eo-foreign.csv")
                );
                MatcherAssert.assertThat(
                    "Foreign must contain only 3 references to objects, but it doesn't",
                    foreign.size(),
                    Matchers.equalTo(3)
                );
                MatcherAssert.assertThat(
                    "Foreign must contain reference to the Number object, but it doesn't",
                    foreign.getById("org.eolang.number").exists("id"),
                    Matchers.is(true)
                );
                MatcherAssert.assertThat(
                    "Foreign must contain reference to the Bytes object, but it doesn't",
                    foreign.getById("org.eolang.bytes").exists("id"),
                    Matchers.is(true)
                );
                MatcherAssert.assertThat(
                    "Foreign must contain reference to the current object, but it doesn't",
                    foreign.getById("foo").exists("id"),
                    Matchers.is(true)
                );
                MatcherAssert.assertThat(
                    "Foreign must not contain a reference to an old object",
                    foreign.select(
                        tojo -> "org.eolang.string".equals(tojo.get("id"))
                    ).isEmpty(),
                    Matchers.is(true)
                );
            }
        );
    }

    @Test
    void removesUnnecessaryPulledObjects(@Mktmp final Path temp) throws Exception {
        new Farea(temp).together(
            f -> {
                f.clean();
                f.files().file("src/main/eo/foo.eo").write(
                    String.join(
                        "\n",
                        "+package org.eolang",
                        "",
                        "# In this program, we refer to the 'String' object by mistake.",
                        "[] > foo",
                        "  \"Hello\" > @"
                    ).getBytes()
                );
                new EOplugin(f).appendItself();
                f.exec("eo:register", "eo:parse", "eo:shake", "eo:probe", "eo:pull");
                f.files().file("src/main/eo/foo.eo").write(
                    String.join(
                        "\n",
                        "+package org.eolang",
                        "",
                        "# Now, this program, doesn't refer to the 'String' object",
                        "[] > foo",
                        "  42 > @"
                    ).getBytes()
                );
                f.exec("eo:register", "eo:parse", "eo:shake", "eo:probe", "eo:pull");
                MatcherAssert.assertThat(
                    "Necessary objects must were pulled",
                    temp.resolve(
                        String.format(
                            "target/eo/%s/org/eolang/number.eo",
                            PullMojo.DIR
                        )
                    ).toFile().exists(),
                    Matchers.is(true)
                );
                MatcherAssert.assertThat(
                    "Unnecessary objects were not removed",
                    temp.resolve(
                        String.format(
                            "target/eo/%s/org/eolang/string.eo",
                            PullMojo.DIR
                        )
                    ).toFile().exists(),
                    Matchers.is(false)
                );
            }
        );
    }

    private static TjSmart foreign(final Path path) {
        return new TjSmart(
            Catalogs.INSTANCE.make(path)
        );
    }
}
