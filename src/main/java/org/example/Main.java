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
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Nurses problem with schedule requests. */
public class Main {
    public static void main(String[] args) {
        Loader.loadNativeLibraries();
        final int numNurses = 5;
        final int numDays = 7;
        final int numShifts = 3;

        final int[] allNurses = IntStream.range(0, numNurses).toArray();
        final int[] allDays = IntStream.range(0, numDays).toArray();
        final int[] allShifts = IntStream.range(0, numShifts).toArray();

        final int[][][] shiftRequests = new int[][][]{
                {
                        {0, 0, 1},
                        {0, 0, 0},
                        {0, 0, 0},
                        {0, 0, 0},
                        {0, 0, 1},
                        {0, 1, 0},
                        {0, 0, 1},
                },
                {
                        {0, 0, 0},
                        {0, 0, 0},
                        {0, 1, 0},
                        {0, 1, 0},
                        {1, 0, 0},
                        {0, 0, 0},
                        {0, 0, 1},
                },
                {
                        {0, 1, 0},
                        {0, 1, 0},
                        {0, 0, 0},
                        {1, 0, 0},
                        {0, 0, 0},
                        {0, 1, 0},
                        {0, 0, 0},
                },
                {
                        {0, 0, 1},
                        {0, 0, 0},
                        {1, 0, 0},
                        {0, 1, 0},
                        {0, 0, 0},
                        {1, 0, 0},
                        {0, 0, 0},
                },
                {
                        {0, 0, 0},
                        {0, 0, 1},
                        {0, 1, 0},
                        {0, 0, 0},
                        {1, 0, 0},
                        {0, 1, 0},
                        {0, 0, 0},
                },
        };

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
            LinearExprBuilder numShiftsWorked = LinearExpr.newBuilder();
            for (int d : allDays) {
                for (int s : allShifts) {
                    numShiftsWorked.add(shifts[n][d][s]);
                }
            }
            model.addLinearConstraint(numShiftsWorked, minShiftsPerNurse, maxShiftsPerNurse);
        }

        LinearExprBuilder obj = LinearExpr.newBuilder();
        for (int n : allNurses) {
            for (int d : allDays) {
                for (int s : allShifts) {
                    obj.addTerm(shifts[n][d][s], shiftRequests[n][d][s]);
                }
            }
        }
        model.maximize(obj);

        // Creates a solver and solves the model.
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            System.out.printf("Solution:%n");
            for (int d : allDays) {
                System.out.printf("Day %d%n", d);
                for (int n : allNurses) {
                    for (int s : allShifts) {
                        if (solver.booleanValue(shifts[n][d][s])) {
                            if (shiftRequests[n][d][s] == 1) {
                                System.out.printf("  Nurse %d works shift %d (requested).%n", n, s);
                            } else {
                                System.out.printf("  Nurse %d works shift %d (not requested).%n", n, s);
                            }
                        }
                    }
                }
            }
            System.out.printf("Number of shift requests met = %f (out of %d)%n", solver.objectiveValue(),
                    numNurses * minShiftsPerNurse);
        } else {
            System.out.printf("No optimal solution found !");
        }
        // Statistics.
        System.out.println("Statistics");
        System.out.printf("  conflicts: %d%n", solver.numConflicts());
        System.out.printf("  branches : %d%n", solver.numBranches());
        System.out.printf("  wall time: %f s%n", solver.wallTime());
    }

    private Main() {
    }
}
