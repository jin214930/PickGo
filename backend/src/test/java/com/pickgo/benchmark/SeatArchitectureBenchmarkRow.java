package com.pickgo.benchmark;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "seat_architecture_benchmark_row",
        indexes = @Index(
                name = "uq_benchmark_seat",
                columnList = "kind, session_no, area_no, seat_row, seat_number",
                unique = true
        )
)
public class SeatArchitectureBenchmarkRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(name = "session_no", nullable = false)
    private int sessionNo;

    @Column(name = "area_no", nullable = false)
    private int areaNo;

    @Column(name = "seat_row", length = 16)
    private String seatRow;

    @Column(name = "seat_number")
    private Integer seatNumber;

    @Column(length = 16)
    private String status;

    protected SeatArchitectureBenchmarkRow() {
    }

    private SeatArchitectureBenchmarkRow(
            Kind kind,
            int sessionNo,
            int areaNo,
            String seatRow,
            Integer seatNumber,
            String status
    ) {
        this.kind = kind;
        this.sessionNo = sessionNo;
        this.areaNo = areaNo;
        this.seatRow = seatRow;
        this.seatNumber = seatNumber;
        this.status = status;
    }

    public static SeatArchitectureBenchmarkRow metadata(Kind kind, int sessionNo, int areaNo) {
        return new SeatArchitectureBenchmarkRow(kind, sessionNo, areaNo, null, null, null);
    }

    public static SeatArchitectureBenchmarkRow seat(int sessionNo, int areaNo, String seatRow, int seatNumber) {
        return new SeatArchitectureBenchmarkRow(
                Kind.SEAT,
                sessionNo,
                areaNo,
                seatRow,
                seatNumber,
                "AVAILABLE"
        );
    }

    public enum Kind {
        PERFORMANCE,
        SESSION,
        AREA,
        SEAT
    }
}
