package ai.causa.quarkusperf.service;

import ai.causa.quarkusperf.model.Booking;
import ai.causa.quarkusperf.repository.BookingRepository;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Business logic for airline booking operations.
 */
@ApplicationScoped
public class BookingService {

    private static final Logger LOG = Logger.getLogger(BookingService.class);

    private static final String[] AIRPORTS   = {"JFK","LAX","ORD","ATL","DFW","DEN","SFO","SEA","LAS","MCO"};
    private static final String[] SEAT_CLASS = {"ECONOMY","BUSINESS","FIRST"};

    @Inject
    BookingRepository bookingRepository;

    @Counted(value = "quarkus_perf_bookings_created_total", description = "Total flight bookings created")
    @Timed(value = "quarkus_perf_booking_create", description = "Time to create a flight booking")
    public Booking createBooking(String passengerId, String passengerName,
                                 String origin, String destination) {

        String correlationId = UUID.randomUUID().toString();
        LOG.infof("[%s] Creating booking for passenger=%s route=%s→%s",
                correlationId, passengerId, origin, destination);

        Random rng = new Random();
        Booking b = new Booking();
        b.setBookingRef("BKG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        b.setPassengerId(passengerId);
        b.setPassengerName(passengerName);
        b.setOrigin(origin.toUpperCase());
        b.setDestination(destination.toUpperCase());
        b.setFlightNumber("QP" + (100 + rng.nextInt(900)));
        b.setDepartureTime(Instant.now().plus(1, ChronoUnit.DAYS));
        b.setArrivalTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS));
        b.setSeatClass(SEAT_CLASS[rng.nextInt(SEAT_CLASS.length)]);
        b.setSeatNumber((char) ('A' + rng.nextInt(6)) + String.valueOf(1 + rng.nextInt(30)));
        b.setFare(BigDecimal.valueOf(100 + rng.nextInt(900)));
        b.setCurrency("USD");
        b.setStatus(Booking.BookingStatus.CONFIRMED);
        b.setBookedAt(Instant.now());
        b.setCorrelationId(correlationId);

        bookingRepository.save(b);

        LOG.infof("[%s] Booking %s CONFIRMED flight=%s seat=%s %s fare=%s",
                correlationId, b.getBookingRef(), b.getFlightNumber(),
                b.getSeatClass(), b.getSeatNumber(), b.getFare());

        return b;
    }

    @Timed(value = "quarkus_perf_booking_lookup", description = "Time to look up a booking by reference")
    public Optional<Booking> getBooking(String bookingRef) {
        return bookingRepository.findByRef(bookingRef);
    }

    @Timed(value = "quarkus_perf_booking_list", description = "Time to list bookings for a passenger")
    public List<Booking> listBookings(String passengerId) {
        return bookingRepository.findByPassenger(passengerId);
    }

    /** Generates a random booking using seeded passenger and airport data (for load generator). */
    public Booking createRandomBooking() {
        Random rng = new Random();
        String pid  = "PAX-" + String.format("%04d", rng.nextInt(1000));
        String name = "Passenger-" + pid;
        String origin      = AIRPORTS[rng.nextInt(AIRPORTS.length)];
        String destination = AIRPORTS[rng.nextInt(AIRPORTS.length)];
        if (origin.equals(destination)) {
            destination = AIRPORTS[(Arrays.asList(AIRPORTS).indexOf(origin) + 1) % AIRPORTS.length];
        }
        return createBooking(pid, name, origin, destination);
    }
}
