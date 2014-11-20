/* Copyright 2013-2014 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.rugged.api;

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.commons.math3.geometry.euclidean.threed.FieldVector3D;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.util.FastMath;
import org.orekit.frames.Transform;
import org.orekit.rugged.utils.SpacecraftToObservedBody;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

/** Class dedicated to find when ground point crosses mean sensor plane.
 * <p>
 * This class is used in the first stage of inverse location.
 * </p>
 * @author Luc Maisonobe
 */
class SensorMeanPlaneCrossing {

    /** Converter between spacecraft and body. */
    private final SpacecraftToObservedBody scToBody;

    /** Middle line. */
    private final double midLine;

    /** Body to inertial transform for middle line. */
    private final Transform midBodyToInert;

    /** Spacecraft to inertial transform for middle line. */
    private final Transform midScToInert;

    /** Minimum line number in the search interval. */
    private final int minLine;

    /** Maximum line number in the search interval. */
    private final int maxLine;

    /** Flag for light time correction. */
    private final boolean lightTimeCorrection;

    /** Flag for aberration of light correction. */
    private final boolean aberrationOfLightCorrection;

    /** Line sensor. */
    private final LineSensor sensor;

    /** Sensor mean plane normal. */
    private final Vector3D meanPlaneNormal;

    /** Maximum number of evaluations. */
    private final int maxEval;

    /** Accuracy to use for finding crossing line number. */
    private final double accuracy;

    /** Simple constructor.
     * @param sensor sensor to consider
     * @param scToBody converter between spacecraft and body
     * @param minLine minimum line number
     * @param maxLine maximum line number
     * @param lightTimeCorrection flag for light time correction
     * @param aberrationOfLightCorrection flag for aberration of light correction.
     * @param maxEval maximum number of evaluations
     * @param accuracy accuracy to use for finding crossing line number
     * @exception RuggedException if some frame conversion fails
     */
    public SensorMeanPlaneCrossing(final LineSensor sensor,
                                   final SpacecraftToObservedBody scToBody,
                                   final int minLine, final int maxLine,
                                   final boolean lightTimeCorrection,
                                   final boolean aberrationOfLightCorrection,
                                   final int maxEval, final double accuracy)
        throws RuggedException {

        this.sensor                      = sensor;
        this.minLine                     = minLine;
        this.maxLine                     = maxLine;
        this.lightTimeCorrection         = lightTimeCorrection;
        this.aberrationOfLightCorrection = aberrationOfLightCorrection;
        this.maxEval                     = maxEval;
        this.accuracy                    = accuracy;
        this.scToBody                    = scToBody;

        this.midLine                     = 0.5 * (minLine + maxLine);
        final AbsoluteDate midDate       = sensor.getDate(midLine);
        this.midBodyToInert              = scToBody.getBodyToInertial(midDate);
        this.midScToInert                = scToBody.getScToInertial(midDate);

        this.meanPlaneNormal             = computeMeanPlaneNormal(midDate);

    }

    /** Compute the plane containing origin that best fits viewing directions point cloud.
     * @param midDate middle date
     * <p>
     * The normal is oriented such traversing pixels in increasing indices
     * order corresponds is consistent with trigonometric order (i.e.
     * counterclockwise).
     * </p>
     * @return normal of the mean plane
     */
    private Vector3D computeMeanPlaneNormal(final AbsoluteDate midDate) {

        // build a centered data matrix
        // (for each viewing direction, we add both the direction and its
        //  opposite, thus ensuring the plane will contain origin)
        final RealMatrix matrix = MatrixUtils.createRealMatrix(3, 2 * sensor.getNbPixels());
        for (int i = 0; i < sensor.getNbPixels(); ++i) {
            final Vector3D l = sensor.getLos(midDate, i);
            matrix.setEntry(0, 2 * i,      l.getX());
            matrix.setEntry(1, 2 * i,      l.getY());
            matrix.setEntry(2, 2 * i,      l.getZ());
            matrix.setEntry(0, 2 * i + 1, -l.getX());
            matrix.setEntry(1, 2 * i + 1, -l.getY());
            matrix.setEntry(2, 2 * i + 1, -l.getZ());
        }

        // compute Singular Value Decomposition
        final SingularValueDecomposition svd = new SingularValueDecomposition(matrix);

        // extract the left singular vector corresponding to least singular value
        // (i.e. last vector since Apache Commons Math returns the values
        //  in non-increasing order)
        final Vector3D singularVector = new Vector3D(svd.getU().getColumn(2)).normalize();

        // check rotation order
        final Vector3D first = sensor.getLos(midDate, 0);
        final Vector3D last  = sensor.getLos(midDate, sensor.getNbPixels() - 1);
        if (Vector3D.dotProduct(singularVector, Vector3D.crossProduct(first, last)) >= 0) {
            return singularVector;
        } else {
            return singularVector.negate();
        }

    }

    /** Get the minimum line number in the search interval.
     * @return minimum line number in the search interval
     */
    public int getMinLine() {
        return minLine;
    }

    /** Get the maximum line number in the search interval.
     * @return maximum line number in the search interval
     */
    public int getMaxLine() {
        return maxLine;
    }

    /** Get the mean plane normal.
     * <p>
     * The normal is oriented such traversing pixels in increasing indices
     * order corresponds is consistent with trigonometric order (i.e.
     * counterclockwise).
     * </p>
     * @return mean plane normal
     */
    public Vector3D getMeanPlaneNormal() {
        return meanPlaneNormal;
    }

    /** Container for mean plane crossing result. */
    public static class CrossingResult {

        /** Crossing date. */
        private final AbsoluteDate crossingDate;

        /** Crossing line. */
        private final double crossingLine;

        /** Target direction in spacecraft frame. */
        private final FieldVector3D<DerivativeStructure> targetDirection;

        /** Simple constructor.
         * @param crossingDate crossing date
         * @param crossingLine crossing line
         * @param targetDirection target direction in spacecraft frame
         */
        private CrossingResult(final AbsoluteDate crossingDate, final double crossingLine,
                               final FieldVector3D<DerivativeStructure> targetDirection) {
            this.crossingDate    = crossingDate;
            this.crossingLine    = crossingLine;
            this.targetDirection = targetDirection;
        }

        /** Get the crossing date.
         * @return crossing date
         */
        public AbsoluteDate getDate() {
            return crossingDate;
        }

        /** Get the crossing line.
         * @return crossing line
         */
        public double getLine() {
            return crossingLine;
        }

        /** Get the normalized target direction in spacecraft frame at crossing.
         * @return normalized target direction in spacecraft frame at crossing, with its first derivative
         * with respect to line number
         */
        public FieldVector3D<DerivativeStructure> getTargetDirection() {
            return targetDirection;
        }

    }

    /** Find mean plane crossing.
     * @param target target ground point
     * @return line number and target direction at mean plane crossing,
     * or null if search interval does not bracket a solution
     * @exception RuggedException if geometry cannot be computed for some line or
     * if the maximum number of evaluations is exceeded
     */
    public CrossingResult find(final Vector3D target)
        throws RuggedException {

        final PVCoordinates targetPV = new PVCoordinates(target, Vector3D.ZERO);

        // we don't use an Apache Commons Math solver here because we are more
        // interested in reducing the number of evaluations than being accurate,
        // as we know the solution is improved in the second stage of inverse location.
        // We expect two or three evaluations only. Each new evaluation shows up quickly in
        // the performances as it involves frames conversions
        double  crossingLine  = midLine;
        Transform bodyToInert = midBodyToInert;
        Transform scToInert   = midScToInert;
        boolean atMin         = false;
        boolean atMax         = false;
        for (int i = 0; i < maxEval; ++i) {

            final FieldVector3D<DerivativeStructure> targetDirection =
                    evaluateLine(crossingLine, targetPV, bodyToInert, scToInert);
            final DerivativeStructure beta = FieldVector3D.angle(targetDirection, meanPlaneNormal);

            final double deltaL = (0.5 * FastMath.PI - beta.getValue()) / beta.getPartialDerivative(1);
            if (FastMath.abs(deltaL) <= accuracy) {
                // return immediately, without doing any additional evaluation!
                return new CrossingResult(sensor.getDate(crossingLine), crossingLine, targetDirection);
            }
            crossingLine += deltaL;

            if (crossingLine < minLine) {
                if (atMin) {
                    // we were already trying at minLine and we need to go below that
                    // give up as the solution is out of search interval
                    return null;
                }
                atMin        = true;
                crossingLine = minLine;
            } else if (crossingLine > maxLine) {
                if (atMax) {
                    // we were already trying at maxLine and we need to go above that
                    // give up as the solution is out of search interval
                    return null;
                }
                atMax        = true;
                crossingLine = maxLine;
            } else {
                // the next evaluation will be a regular point
                atMin = false;
                atMax = false;
            }

            final AbsoluteDate date = sensor.getDate(crossingLine);
            bodyToInert = scToBody.getBodyToInertial(date);
            scToInert   = scToBody.getScToInertial(date);
        }

        return null;

    }

    /** Evaluate geometry for a given line number.
     * @param lineNumber current line number
     * @param targetPV target ground point
     * @param bodyToInert transform from observed body to inertial frame, for current line
     * @param scToInert transform from inertial frame to spacecraft frame, for current line
     * @return target direction in spacecraft frame, with its first derivative
     * with respect to line number
     */
    private FieldVector3D<DerivativeStructure> evaluateLine(final double lineNumber, final PVCoordinates targetPV,
                                                            final Transform bodyToInert, final Transform scToInert) {

        // compute the transform between spacecraft and observed body
        final PVCoordinates refInert =
                scToInert.transformPVCoordinates(new PVCoordinates(sensor.getPosition(), Vector3D.ZERO));

        final PVCoordinates targetInert;
        if (lightTimeCorrection) {
            // apply light time correction
            final Vector3D iT     = bodyToInert.transformPosition(targetPV.getPosition());
            final double   deltaT = refInert.getPosition().distance(iT) / Constants.SPEED_OF_LIGHT;
            targetInert           = bodyToInert.shiftedBy(-deltaT).transformPVCoordinates(targetPV);
        } else {
            // don't apply light time correction
            targetInert = bodyToInert.transformPVCoordinates(targetPV);
        }

        final PVCoordinates lInert    = new PVCoordinates(refInert, targetInert);
        final Transform     inertToSc = scToInert.getInverse();
        final Vector3D obsLInert;
        final Vector3D obsLInertDot;
        if (aberrationOfLightCorrection) {

            // apply aberration of light correction
            // as the spacecraft velocity is small with respect to speed of light,
            // we use classical velocity addition and not relativistic velocity addition
            // we have: c * lInert + vsat = k * obsLInert
            final PVCoordinates spacecraftPV = scToInert.transformPVCoordinates(PVCoordinates.ZERO);
            final Vector3D  l        = lInert.getPosition().normalize();
            final Vector3D  lDot     = normalizedDot(lInert.getPosition(), lInert.getVelocity());
            final Vector3D  kObs     = new Vector3D(Constants.SPEED_OF_LIGHT, l, +1.0, spacecraftPV.getVelocity());
            obsLInert = kObs.normalize();

            // the following derivative is computed under the assumption the spacecraft velocity
            // is constant in inertial frame ... It is obviously not true, but as this velocity
            // is very small with respect to speed of light, the error is expected to remain small
            obsLInertDot = normalizedDot(kObs, new Vector3D(Constants.SPEED_OF_LIGHT, lDot));

        } else {

            // don't apply aberration of light correction
            obsLInert    = lInert.getPosition().normalize();
            obsLInertDot = normalizedDot(lInert.getPosition(), lInert.getVelocity());

        }
        final Vector3D dir    = inertToSc.transformVector(obsLInert);
        final Vector3D dirDot = new Vector3D(+1.0, inertToSc.transformVector(obsLInertDot),
                                             -1.0, Vector3D.crossProduct(inertToSc.getRotationRate(), dir));

        // combine vector value and derivative
        final double rate = sensor.getRate(lineNumber);
        return new FieldVector3D<DerivativeStructure>(new DerivativeStructure(1, 1, dir.getX(), dirDot.getX() / rate),
                                                      new DerivativeStructure(1, 1, dir.getY(), dirDot.getY() / rate),
                                                      new DerivativeStructure(1, 1, dir.getZ(), dirDot.getZ() / rate));

    }

    /** Compute the derivative of normalized vector.
     * @param u base vector
     * @param uDot derivative of the base vector
     * @return vDot, where v = u / ||u||
     */
    private Vector3D normalizedDot(final Vector3D u, final Vector3D uDot) {
        final double n = u.getNorm();
        return new Vector3D(1.0 / n, uDot, -Vector3D.dotProduct(u, uDot) / (n * n * n), u);
    }

}
