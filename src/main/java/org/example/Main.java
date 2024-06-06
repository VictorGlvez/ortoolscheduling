//package org.example;
//
//import com.google.ortools.Loader;
//import com.google.ortools.sat.CpModel;
//import com.google.ortools.sat.CpSolver;
//import com.google.ortools.sat.CpSolverStatus;
//import com.google.ortools.sat.Literal;
//
//public class Main {
//    public static void main(String[] args) {
//        Loader.loadNativeLibraries();
//
//        final int numNurses = 5;
//        final int numDays = 7;
//        final int numShifts = 3;
//
//        CpModel model = new CpModel();
//
//        Literal[][][] shifts = new Literal[numNurses][numDays][numShifts];
//        for (int n = 0; n < numNurses; n++) {
//            for (int d = 0; d < numDays; d++) {
//                for (int s = 0; s < numShifts; s++) {
//                    shifts[n][d][s] = model.newBoolVar("shifts_n" + n + "d" + d + "s" + s);
//                }
//            }
//        }
//
//        // Simplified constraints and objectives for testing
//        CpSolver solver = new CpSolver();
//        CpSolverStatus status = solver.solve(model);
//
//        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
//            System.out.println("Solution found!");
//        } else {
//            System.out.println("No solution found.");
//        }
//    }
//}


package org.example;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Main {
    public static void main(String[] args) {
        Loader.loadNativeLibraries();

        final int numNurses = 10;
        final int numDays = 360;
        final int numShifts = 3;
        final int vacationDays = 30;  // Número de días de vacaciones seguidos

        final int[] allNurses = IntStream.range(0, numNurses).toArray();
        final int[] allDays = IntStream.range(0, numDays).toArray();
        final int[] allShifts = IntStream.range(0, numShifts).toArray();

        // Creates the model.
        CpModel model = new CpModel();

        // Creates shift variables.
        // shifts[(n, d, s)]: nurse 'n' works shift 's' on day 'd'.
        Literal[][][] shifts = new Literal[numNurses][numDays][numShifts];
        for (int n : allNurses) {
            for (int d : allDays) {
                for (int s : allShifts) {
                    shifts[n][d][s] = model.newBoolVar("shifts_n" + n + "d" + d + "s" + s);
                }
            }
        }

        // Each shift is assigned to exactly one nurse in the schedule period.
        for (int d : allDays) {
            for (int s : allShifts) {
                List<Literal> nurses = new ArrayList<>();
                for (int n : allNurses) {
                    nurses.add(shifts[n][d][s]);
                }
                model.addExactlyOne(nurses);
            }
        }

        // Each nurse works at most one shift per day.
        for (int n : allNurses) {
            for (int d : allDays) {
                List<Literal> work = new ArrayList<>();
                for (int s : allShifts) {
                    work.add(shifts[n][d][s]);
                }
                model.addAtMostOne(work);
            }
        }

        // Try to distribute the shifts evenly, so that each nurse works
        // minShiftsPerNurse shifts. If this is not possible, because the total
        // number of shifts is not divisible by the number of nurses, some nurses will
        // be assigned one more shift.
        int minShiftsPerNurse = (numShifts * numDays) / numNurses;
        int maxShiftsPerNurse;
        if ((numShifts * numDays) % numNurses == 0) {
            maxShiftsPerNurse = minShiftsPerNurse;
        } else {
            maxShiftsPerNurse = minShiftsPerNurse + 1;
        }
        for (int n : allNurses) {
            LinearExprBuilder shiftsWorked = LinearExpr.newBuilder();
            for (int d : allDays) {
                for (int s : allShifts) {
                    shiftsWorked.add(shifts[n][d][s]);
                }
            }
            model.addLinearConstraint(shiftsWorked, minShiftsPerNurse, maxShiftsPerNurse);
        }

        // Adding vacation constraints for each nurse
        for (int n : allNurses) {
            // Randomly assign a start day for the vacation
            int vacationStart = (int) (Math.random() * (numDays - vacationDays));
            for (int d = vacationStart; d < vacationStart + vacationDays; d++) {
                for (int s : allShifts) {
                    model.addEquality(shifts[n][d][s], 0);
                }
            }
        }

        CpSolver solver = new CpSolver();
        solver.getParameters().setLinearizationLevel(0);
        // Tell the solver to enumerate all solutions.
        solver.getParameters().setEnumerateAllSolutions(true);

        // Display the first five solutions.
        final int solutionLimit = 5;
        class VarArraySolutionPrinterWithLimit extends CpSolverSolutionCallback {
            public VarArraySolutionPrinterWithLimit(
                    int[] allNurses, int[] allDays, int[] allShifts, Literal[][][] shifts, int limit) {
                solutionCount = 0;
                this.allNurses = allNurses;
                this.allDays = allDays;
                this.allShifts = allShifts;
                this.shifts = shifts;
                solutionLimit = limit;
            }

            @Override
            public void onSolutionCallback() {
                System.out.printf("Solution #%d:%n", solutionCount);
                for (int d : allDays) {
                    System.out.printf("Day %d%n", d);
                    for (int n : allNurses) {
                        boolean isWorking = false;
                        for (int s : allShifts) {
                            if (booleanValue(shifts[n][d][s])) {
                                isWorking = true;
                                System.out.printf("  Nurse %d work shift %d%n", n, s);
                            }
                        }
                        if (!isWorking) {
                            System.out.printf("  Nurse %d does not work%n", n);
                        }
                    }
                }
                solutionCount++;
                if (solutionCount >= solutionLimit) {
                    System.out.printf("Stop search after %d solutions%n", solutionLimit);
                    stopSearch();
                }
            }

            public int getSolutionCount() {
                return solutionCount;
            }

            private int solutionCount;
            private final int[] allNurses;
            private final int[] allDays;
            private final int[] allShifts;
            private final Literal[][][] shifts;
            private final int solutionLimit;
        }

        VarArraySolutionPrinterWithLimit cb =
                new VarArraySolutionPrinterWithLimit(allNurses, allDays, allShifts, shifts, solutionLimit);

        // Creates a solver and solves the model.
        CpSolverStatus status = solver.solve(model, cb);
        System.out.println("Status: " + status);
        System.out.println(cb.getSolutionCount() + " solutions found.");

        // Statistics.
        System.out.println("Statistics");
        System.out.printf("  conflicts: %d%n", solver.numConflicts());
        System.out.printf("  branches : %d%n", solver.numBranches());
        System.out.printf("  wall time: %f s%n", solver.wallTime());
    }

    private Main() {
    }
}
