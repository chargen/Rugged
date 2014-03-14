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
package org.orekit.rugged.core.dem;

import org.apache.commons.math3.util.FastMath;
import org.orekit.rugged.api.RuggedException;
import org.orekit.rugged.api.RuggedMessages;


/** Simple implementation of a {@link Tile}.
 * @see SimpleTileFactory
 * @author Luc Maisonobe
 */
public class SimpleTile implements Tile {

    /** Minimum latitude. */
    private double minLatitude;

    /** Minimum longitude. */
    private double minLongitude;

    /** Step in latitude (size of one raster element). */
    private double latitudeStep;

    /** Step in longitude (size of one raster element). */
    private double longitudeStep;

    /** Number of latitude rows. */
    private int latitudeRows;

    /** Number of longitude columns. */
    private int longitudeColumns;

    /** Minimum elevation. */
    private double minElevation;

    /** Maximum elevation. */
    private double maxElevation;

    /** Elevation array. */
    private double[] elevations;

    /** Simple constructor.
     * <p>
     * Creates an empty tile.
     * </p>
     */
    protected SimpleTile() {
    }

    /** {@inheritDoc} */
    @Override
    public void setGeometry(final double minLatitude, final double minLongitude,
                            final double latitudeStep, final double longitudeStep,
                            final int latitudeRows, final int longitudeColumns)
        throws RuggedException {
        this.minLatitude      = minLatitude;
        this.minLongitude     = minLongitude;
        this.latitudeStep     = latitudeStep;
        this.longitudeStep    = longitudeStep;
        this.latitudeRows     = latitudeRows;
        this.longitudeColumns = longitudeColumns;
        this.minElevation     = Double.POSITIVE_INFINITY;
        this.maxElevation     = Double.NEGATIVE_INFINITY;

        if (latitudeRows < 1 || longitudeColumns < 1) {
            throw new RuggedException(RuggedMessages.EMPTY_TILE, latitudeRows, longitudeColumns);
        }
        this.elevations = new double[latitudeRows * longitudeColumns];

    }

    /** {@inheritDoc} */
    @Override
    public void tileUpdateCompleted() throws RuggedException {
        processUpdatedElevation(elevations);
    }

    /** Process elevation array at completion.
     * </p>
     * This method is called at tile update completion, it is
     * expected to be overridden by subclasses. The default
     * implementation does nothing.
     * </p>
     * @param elevations elevations array
     */
    protected void processUpdatedElevation(final double[] elevations) {
        // do nothing by default
    }

    /** {@inheritDoc} */
    @Override
    public double getMinimumLatitude() {
        return minLatitude;
    }

    /** {@inheritDoc} */
    @Override
    public double getMaximumLatitude() {
        return minLatitude + latitudeStep * (latitudeRows - 1);
    }

    /** {@inheritDoc} */
    @Override
    public double getMinimumLongitude() {
        return minLongitude;
    }

    /** {@inheritDoc} */
    @Override
    public double getMaximumLongitude() {
        return minLongitude + longitudeStep * (longitudeColumns - 1);
    }

    /** {@inheritDoc} */
    @Override
    public double getLatitudeStep() {
        return latitudeStep;
    }

    /** {@inheritDoc} */
    @Override
    public double getLongitudeStep() {
        return longitudeStep;
    }

    /** {@inheritDoc} */
    @Override
    public int getLatitudeRows() {
        return latitudeRows;
    }

    /** {@inheritDoc} */
    @Override
    public int getLongitudeColumns() {
        return longitudeColumns;
    }

    /** {@inheritDoc} */
    @Override
    public double getMinElevation() {
        return minElevation;
    }

    /** {@inheritDoc} */
    @Override
    public double getMaxElevation() {
        return maxElevation;
    }

    /** {@inheritDoc} */
    @Override
    public void setElevation(final int latitudeIndex, final int longitudeIndex,
                             final double elevation) throws RuggedException {
        if (latitudeIndex  < 0 || latitudeIndex  > (latitudeRows - 1) ||
            longitudeIndex < 0 || longitudeIndex > (longitudeColumns - 1)) {
            throw new RuggedException(RuggedMessages.OUT_OF_TILE_INDICES,
                                      latitudeIndex, longitudeIndex,
                                      latitudeRows - 1, longitudeColumns - 1);
        }        
        minElevation = FastMath.min(minElevation, elevation);
        maxElevation = FastMath.max(maxElevation, elevation);
        elevations[latitudeIndex * getLongitudeColumns() + longitudeIndex] = elevation;
    }

    /** {@inheritDoc} */
    @Override
    public double getElevationAtIndices(int latitudeIndex, int longitudeIndex) {
        return elevations[latitudeIndex * getLongitudeColumns() + longitudeIndex];
    }

    /** {@inheritDoc} */
    @Override
    public int getLatitudeIndex(double latitude) {
        return (int) FastMath.floor((latitude  - minLatitude)  / latitudeStep);
    }

    /** {@inheritDoc} */
    @Override
    public int getLontitudeIndex(double longitude) {
        return (int) FastMath.floor((longitude - minLongitude) / longitudeStep);
    }

    /** {@inheritDoc} */
    @Override
    public Location getLocation(final double latitude, final double longitude) {
        final int latitudeIndex  = getLatitudeIndex(latitude);
        final int longitudeIndex = getLontitudeIndex(longitude);
        if (longitudeIndex < 0) {
            if (latitudeIndex < 0) {
                return Location.SOUTH_WEST;
            } else if (latitudeIndex <= (latitudeRows - 2)) {
                return Location.WEST;
            } else {
                return Location.NORTH_WEST;
            }
        } else if (longitudeIndex <= (longitudeColumns - 2)) {
            if (latitudeIndex < 0) {
                return Location.SOUTH;
            } else if (latitudeIndex <= (latitudeRows - 2)) {
                return Location.IN_TILE;
            } else {
                return Location.NORTH;
            }
        } else {
            if (latitudeIndex < 0) {
                return Location.SOUTH_EAST;
            } else if (latitudeIndex <= (latitudeRows - 2)) {
                return Location.EAST;
            } else {
                return Location.NORTH_EAST;
            }
        }
    }

}
