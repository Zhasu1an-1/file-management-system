package com.example.filemanagementsystem.file;

import com.example.filemanagementsystem.api.Users;
import com.example.filemanagementsystem.model.FileMetadata;
import com.example.filemanagementsystem.model.Role;
import com.example.filemanagementsystem.model.User;
import com.example.filemanagementsystem.repository.FileRepository;
import com.example.filemanagementsystem.repository.FileShareRepository;
import com.example.filemanagementsystem.repository.FolderRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@ExtendWith(MockitoExtension.class)
class ProtectionProxyTest {
    private final UUID id = UUID.randomUUID();

    @Mock RealFileService real;
    @Mock Users users;
    @Mock FileRepository files;
    @Mock FileShareRepository shares;
    @Mock FolderRepository folders;
    @Mock User bob;
    @Mock User alice;
    @Mock FileMetadata file;
    @InjectMocks ProtectionProxy proxy;

    @Test
    void deniesPrivateReadsBeforeCallingTheRealSubject() {
        givenPrivateFile();

        assertThatThrownBy(() -> proxy.read("bob", id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(FORBIDDEN));
        verifyNoInteractions(real);
    }

    @Test
    void delegatesSharedReadsToTheRealSubject() {
        givenPrivateFile();
        when(shares.existsByFileIdAndViewerUsername(id, "bob")).thenReturn(true);
        var details = new FileInfo.Details(null, "shared contents");
        when(real.read("bob", id)).thenReturn(details);

        assertThat(proxy.read("bob", id)).isSameAs(details);
        verify(real).read("bob", id);
    }

    @Test
    void viewerMutationsNeverReachTheRealSubject() {
        when(users.require("bob")).thenReturn(bob);
        when(bob.getRole()).thenReturn(Role.VIEWER);

        assertThatThrownBy(() -> proxy.rename("bob", id, new FileRequests.Rename("new.txt")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> proxy.delete("bob", id)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> proxy.share("bob", id, new FileRequests.Share("alice")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> proxy.move("bob", id, new FileRequests.Move(null)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(real);
    }

    @Test
    void ownerRoleCannotDeleteSomeoneElsesFile() {
        when(users.require("alice")).thenReturn(alice);
        when(alice.getRole()).thenReturn(Role.OWNER);
        when(files.findForUpdate(id)).thenReturn(Optional.of(file));
        when(file.getOwner()).thenReturn(bob);
        when(bob.getUsername()).thenReturn("other-owner");

        assertThatThrownBy(() -> proxy.delete("alice", id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(FORBIDDEN));
        verifyNoInteractions(real);
    }

    private void givenPrivateFile() {
        when(users.require("bob")).thenReturn(bob);
        when(bob.getRole()).thenReturn(Role.VIEWER);
        when(files.findForRead(id)).thenReturn(Optional.of(file));
    }
}
