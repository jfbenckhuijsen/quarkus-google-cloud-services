package io.quarkiverse.googlecloudservices.it;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;

@Path("/firestore")
public class FirestoreResource {
    @Inject
    Firestore firestore;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String firestore() throws ExecutionException, InterruptedException {
        // insert 3 persons
        CollectionReference persons = firestore.collection("persons");
        List<ApiFuture<WriteResult>> futures = new ArrayList<>();
        futures.add(persons.document("1").set(new Person(1L, "John", "Doe")));
        futures.add(persons.document("2").set(new Person(2L, "Jane", "Doe")));
        futures.add(persons.document("3").set(new Person(3L, "Charles", "Baudelaire")));
        ApiFutures.allAsList(futures).get();

        // search for lastname=Doe
        Query query = persons.whereEqualTo("lastname", "Doe");
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        return querySnapshot.get().getDocuments().stream()
                .map(document -> document.getId() + " - " + document.getString("firstname") + " "
                        + document.getString("lastname") + "\n")
                .collect(Collectors.joining());
    }
}
