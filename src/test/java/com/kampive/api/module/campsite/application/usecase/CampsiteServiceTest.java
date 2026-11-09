package com.kampive.api.module.campsite.application.usecase;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import com.kampive.api.module.campsite.application.port.in.Campsites.*;
import com.kampive.api.module.campsite.application.port.out.CampsiteRepository;
import com.kampive.api.module.campsite.domain.CampsiteFailure;
import com.kampive.api.module.user.application.port.in.UserAccounts;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampsiteServiceTest {
    final CampsiteRepository repository=mock(CampsiteRepository.class);
    final UserAccounts users=mock(UserAccounts.class);
    final CampsiteService service=new CampsiteService(repository,users);
    final UUID actor=UUID.randomUUID(), uuid=UUID.randomUUID(), category=UUID.randomUUID();
    Content content(List<Photo> photos,List<Contact> contacts,BigDecimal lat,BigDecimal lon,BigDecimal price,UUID currency) {
        return new Content("Test Camp","",category,null,"","","","","","",lat,lon,null,price,currency,List.of(),List.of(),contacts,photos);
    }
    void active() { when(users.findActiveByUuid(actor)).thenReturn(Optional.of(mock(UserAccounts.UserView.class))); }
    @Test void registrationRequiresCompleteContentAndOnlyReturnsPendingReceipt() {
        var full=new Content("Full Camp","Description",category,UUID.randomUUID(),"Street","","","","","",
            BigDecimal.ZERO,BigDecimal.ZERO,null,null,null,List.of(),List.of(),
            List.of(new Contact(UUID.randomUUID(),"phone")),List.of(new Photo("https://example.test/photo","")));
        assertThrows(CampsiteFailure.class,() -> service.register(actor,full));
        verifyNoInteractions(repository);
        active();
        assertEquals(CampsiteFailure.Reason.INVALID_INPUT,assertThrows(CampsiteFailure.class,
            () -> service.register(actor,content(List.of(),List.of(),null,null,null,null))).reason());
        when(repository.register(actor,full)).thenReturn(uuid);
        assertEquals(new Submission(uuid,"pending_review"),service.register(actor,full));
        verify(repository).register(actor,full);
        verifyNoMoreInteractions(repository);
    }
    @Test void suggestionsNeverGrantOwnership() {
        active(); var input=new SuggestionInput("Camp","https://instagram.com/camp/","https://maps.app.goo.gl/example");
        var receipt=new Submission(uuid,"pending_review");
        when(repository.suggest(actor,input)).thenReturn(receipt);
        assertEquals(receipt,service.suggest(actor,input));
        verify(repository).suggest(actor,input); verifyNoMoreInteractions(repository);
    }
    @Test void validatesMinimalSuggestionsAndOwnershipEvidence() {
        assertThrows(CampsiteFailure.class,() -> new SuggestionInput("Ca","https://instagram.com/camp/","https://maps.app.goo.gl/example"));
        assertThrows(CampsiteFailure.class,() -> new SuggestionInput("Camp"," ","https://maps.app.goo.gl/example"));
        assertThrows(CampsiteFailure.class,() -> new SuggestionInput("Camp","https://instagram.com/camp/","x".repeat(2001)));
        assertThrows(CampsiteFailure.class,() -> new ClaimInput("", "Evidence"));
        assertThrows(CampsiteFailure.class,() -> new ClaimInput("Contact", " "));
    }
    @Test void suggestionLinksMustBelongToInstagramProfilesAndGoogleMaps() {
        for (String maps:List.of("https://maps.app.goo.gl/example","https://www.google.com/maps/place/Camp","https://goo.gl/maps/example"))
            assertDoesNotThrow(() -> new SuggestionInput("Camp","https://instagram.com/camp/",maps));
        for (String instagram:List.of("https://instagram.com.evil.test/camp/","https://instagram.com/p/photo","http://instagram.com/camp/"))
            assertThrows(CampsiteFailure.class,() -> new SuggestionInput("Camp",instagram,"https://maps.app.goo.gl/example"));
        for (String maps:List.of("https://google.com.evil.test/maps","https://google.com/search?q=camp","https://example.test/maps"))
            assertThrows(CampsiteFailure.class,() -> new SuggestionInput("Camp","https://instagram.com/camp/",maps));
    }
    @Test void claimsOnlyRecordRequestsAndRejectUnavailableCampsites() {
        active(); var input=new ClaimInput("phone","Please check my ownership");
        when(repository.claim(actor,uuid,input)).thenReturn(Optional.empty());
        assertEquals(CampsiteFailure.Reason.INVALID_STATE,assertThrows(CampsiteFailure.class,() -> service.claim(actor,uuid,input)).reason());
        var receipt=new Submission(UUID.randomUUID(),"pending_review");
        when(repository.claim(actor,uuid,input)).thenReturn(Optional.of(receipt));
        assertEquals(receipt,service.claim(actor,uuid,input));
        verify(repository,never()).update(any(),any()); verify(repository,never()).status(any(),any());
    }
    @Test void validatesPhotoLimitAndSchemesAndDuplicates() {
        var nine=IntStream.range(0,9).mapToObj(i -> new Photo("https://example.test/"+i,"Photo")).toList();
        assertEquals(9,content(nine,List.of(),null,null,null,null).photos().size());
        var ten=new ArrayList<>(nine); ten.add(new Photo("https://example.test/9",""));
        assertThrows(CampsiteFailure.class,() -> content(ten,List.of(),null,null,null,null));
        assertThrows(CampsiteFailure.class,() -> content(List.of(nine.getFirst(),nine.getFirst()),List.of(),null,null,null,null));
        for (String url:List.of("file:///tmp/photo","http://example.test/photo","javascript:alert(1)","https://user:pass@example.test/photo"))
            assertThrows(CampsiteFailure.class,() -> new Photo(url,""));
    }
    @Test void validatesCoordinatesPricesAndDuplicateContactTypes() {
        assertThrows(CampsiteFailure.class,() -> content(List.of(),List.of(),BigDecimal.ZERO,null,null,null));
        assertThrows(CampsiteFailure.class,() -> content(List.of(),List.of(),new BigDecimal("91"),BigDecimal.ZERO,null,null));
        assertThrows(CampsiteFailure.class,() -> content(List.of(),List.of(),null,null,BigDecimal.ONE,null));
        assertThrows(CampsiteFailure.class,() -> content(List.of(),List.of(),null,null,new BigDecimal("1.001"),UUID.randomUUID()));
        UUID type=UUID.randomUUID();
        assertThrows(CampsiteFailure.class,() -> content(List.of(),List.of(new Contact(type,"one"),new Contact(type,"two")),null,null,null,null));
        assertFalse(content(List.of(),List.of(),null,null,null,null).readyForReview());
    }
    @Test void paginationIsBounded() {
        assertThrows(CampsiteFailure.class,() -> new Query(null,null,null,null,null,-1,20));
        assertThrows(CampsiteFailure.class,() -> new Query(null,null,null,null,null,0,51));
        assertThrows(CampsiteFailure.class,() -> new Query(null,null,null,null,"x".repeat(121),0,20));
    }
    @Test void inactiveAccountCannotCreateOrReadOwnedData() {
        when(users.findActiveByUuid(actor)).thenReturn(Optional.empty());
        assertThrows(CampsiteFailure.class,() -> service.suggest(actor,new SuggestionInput("Camp","https://instagram.com/camp/","https://maps.app.goo.gl/example")));
        assertThrows(CampsiteFailure.class,() -> service.owned(actor,uuid));
        verifyNoInteractions(repository);
    }
    @Test void ownershipIsCheckedBeforeAnyMutation() {
        active(); when(repository.lockOwned(uuid,actor)).thenReturn(Optional.empty());
        assertEquals(CampsiteFailure.Reason.NOT_FOUND,assertThrows(CampsiteFailure.class,() -> service.archive(actor,uuid)).reason());
        verify(repository,never()).status(any(),any());
    }
    @Test void publishedPendingAndArchivedCannotBeEdited() {
        active();
        for (String state:List.of("published","pending_review","archived")) {
            when(repository.lockOwned(uuid,actor)).thenReturn(Optional.of(state));
            assertEquals(CampsiteFailure.Reason.INVALID_STATE,assertThrows(CampsiteFailure.class,
                () -> service.update(actor,uuid,content(List.of(),List.of(),null,null,null,null))).reason());
        }
        verify(repository,never()).update(any(),any());
    }
    @Test void incompleteDraftCannotBeSubmitted() {
        active(); when(repository.lockOwned(uuid,actor)).thenReturn(Optional.of("draft"));
        when(repository.find(uuid,actor)).thenReturn(Optional.of(new Detail(null,content(List.of(),List.of(),null,null,null,null))));
        assertThrows(CampsiteFailure.class,() -> service.submit(actor,uuid));
        verify(repository,never()).status(any(),any());
    }
    @Test void publicLookupDoesNotUseOwnerVisibility() {
        when(repository.find(uuid,null)).thenReturn(Optional.empty());
        assertEquals(CampsiteFailure.Reason.NOT_FOUND,assertThrows(CampsiteFailure.class,() -> service.get(uuid)).reason());
        verify(repository).find(uuid,null);
    }
}
