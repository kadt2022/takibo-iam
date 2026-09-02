package com.takibo.installkeys;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La restriction ACL et sa vérification (TAKIBO-INSTALL-KEYS-01).
 * <p>
 * Ces cas ne s'exécutent en production que sur un système à ACL, alors que l'intégration
 * continue tourne sous Linux : sans cette vue simulée, la logique la plus sensible de
 * l'écriture ne serait jamais exercée là où on la vérifie. Le double reproduit les deux
 * comportements observés — un volume qui obéit, et un volume qui conserve des entrées
 * héritées sans lever la moindre exception.
 */
class OwnerOnlyAclTest {

    private static final Path FILE = Path.of("takibo-secrets.env.pending");

    private final StubPrincipal owner = new StubPrincipal("OWNER");

    @Test
    void given_a_compliant_filesystem_then_the_owner_becomes_the_only_allowed_principal()
            throws IOException {
        StubAclView view = new StubAclView(inherited());

        OwnerOnlyAcl.restrict(view, owner, FILE);

        assertThat(view.entries).hasSize(1);
        AclEntry only = view.entries.get(0);
        assertThat(only.principal()).isEqualTo(owner);
        assertThat(only.type()).isEqualTo(AclEntryType.ALLOW);
        assertThat(only.permissions()).contains(AclEntryPermission.READ_DATA);
    }

    @Test
    void given_a_filesystem_that_keeps_an_inherited_entry_then_the_write_is_refused() {
        // Le cas dangereux : setAcl n'echoue pas, mais SYSTEM ou Administrators restent
        // autorises. Sans relecture, le secret serait ecrit dans un fichier lisible par
        // d'autres, sans le moindre signal.
        StubAclView view = new StubAclView(inherited()) {
            @Override
            public void setAcl(List<AclEntry> acl) {
                entries = new ArrayList<>(acl);
                entries.add(allow(new StubPrincipal("SYSTEM")));
            }
        };

        assertThatThrownBy(() -> OwnerOnlyAcl.restrict(view, owner, FILE))
                .isInstanceOf(SecretFileException.class)
                .extracting(e -> ((SecretFileException) e).exitCode())
                .isEqualTo(ExitCode.UNSAFE_FILESYSTEM);
    }

    @Test
    void given_a_filesystem_that_adds_a_deny_entry_then_the_write_is_refused() {
        // Une entree DENY, meme sur le proprietaire, rend l'acces dependant de l'ordre des
        // ACE : ce n'est plus une restriction, c'est une devinette.
        StubAclView view = new StubAclView(List.of()) {
            @Override
            public void setAcl(List<AclEntry> acl) {
                entries = new ArrayList<>(acl);
                entries.add(AclEntry.newBuilder()
                        .setType(AclEntryType.DENY)
                        .setPrincipal(owner)
                        .setPermissions(EnumSet.of(AclEntryPermission.DELETE))
                        .build());
            }
        };

        assertThatThrownBy(() -> OwnerOnlyAcl.restrict(view, owner, FILE))
                .isInstanceOf(SecretFileException.class)
                .hasMessageContaining("refusing to write secrets");
    }

    @Test
    void given_a_filesystem_that_silently_drops_the_acl_then_the_write_is_refused() {
        // Une ACL vide n'est pas une restriction : selon le volume, elle vaut soit tout
        // refuser, soit s'en remettre a l'heritage. Aucun des deux ne se verifie ici.
        StubAclView view = new StubAclView(List.of()) {
            @Override
            public void setAcl(List<AclEntry> acl) {
                entries = new ArrayList<>();
            }
        };

        assertThatThrownBy(() -> OwnerOnlyAcl.restrict(view, owner, FILE))
                .isInstanceOf(SecretFileException.class)
                .extracting(e -> ((SecretFileException) e).exitCode())
                .isEqualTo(ExitCode.UNSAFE_FILESYSTEM);
    }

    @Test
    void given_a_refusal_then_the_message_names_the_file_but_carries_no_content() {
        StubAclView view = new StubAclView(List.of()) {
            @Override
            public void setAcl(List<AclEntry> acl) {
                entries = new ArrayList<>();
            }
        };

        assertThatThrownBy(() -> OwnerOnlyAcl.restrict(view, owner, FILE))
                .hasMessageContaining(FILE.toString());
    }

    // ---------- Doubles ----------

    private List<AclEntry> inherited() {
        return List.of(allow(owner), allow(new StubPrincipal("BUILTIN\\Users")));
    }

    private static AclEntry allow(UserPrincipal principal) {
        return AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(principal)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
    }

    /** Vue ACL en mémoire : obéissante par défaut, désobéissante par redéfinition. */
    private static class StubAclView implements AclFileAttributeView {

        protected List<AclEntry> entries;

        StubAclView(List<AclEntry> initial) {
            this.entries = new ArrayList<>(initial);
        }

        @Override
        public List<AclEntry> getAcl() {
            return List.copyOf(entries);
        }

        @Override
        public void setAcl(List<AclEntry> acl) {
            entries = new ArrayList<>(acl);
        }

        @Override
        public UserPrincipal getOwner() {
            return new StubPrincipal("OWNER");
        }

        @Override
        public void setOwner(UserPrincipal owner) {
            // Hors sujet : l'ecriture ne change jamais de proprietaire.
        }

        @Override
        public String name() {
            return "acl";
        }
    }

    private record StubPrincipal(String name) implements UserPrincipal {

        @Override
        public String getName() {
            return name;
        }
    }
}
