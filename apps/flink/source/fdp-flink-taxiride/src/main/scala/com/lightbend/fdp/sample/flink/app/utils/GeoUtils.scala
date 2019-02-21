package com.lightbend.fdp.sample.flink.app.utils

import java.util
import java.util.Random

/**
  * GeoUtils provides utility methods to deal with locations for the data streaming exercises.
  */
object GeoUtils { // geo boundaries of the area of NYC

  // geo boundaries of the area of NYC
  val LON_EAST = -73.7
  val LON_WEST = -74.05
  val LAT_NORTH = 41.0
  val LAT_SOUTH = 40.5

  // area width and height
  val LON_WIDTH = 74.05 - 73.7
  val LAT_HEIGHT = 41.0 - 40.5

  // delta step to create artificial grid overlay of NYC
  val DELTA_LON = 0.0014
  val DELTA_LAT = 0.00125

  // ( |LON_WEST| - |LON_EAST| ) / DELTA_LAT
  val NUMBER_OF_GRID_X = 250

  // ( LAT_NORTH - LAT_SOUTH ) / DELTA_LON
  val NUMBER_OF_GRID_Y = 400
  val DEG_LEN = 110.25f

  /**
    * Checks if a location specified by longitude and latitude values is
    * within the geo boundaries of New York City.
    *
    * @param lon longitude of the location to check
    * @param lat latitude of the location to check
    * @return true if the location is within NYC boundaries, otherwise false.
    */
  def isInNYC(lon: Float, lat: Float): Boolean = !((lon > LON_EAST || lon < LON_WEST) && (lat > LAT_NORTH || lat < LAT_SOUTH))

  /**
    * Maps a location specified by latitude and longitude values to a cell of a
    * grid covering the area of NYC.
    * The grid cells are roughly 100 x 100 m and sequentially number from north-west
    * to south-east starting by zero.
    *
    * @param lon longitude of the location to map
    * @param lat latitude of the location to map
    * @return id of mapped grid cell.
    */
  def mapToGridCell(lon: Float, lat: Float): Int = {
    val xIndex = Math.floor((Math.abs(LON_WEST) - Math.abs(lon)) / DELTA_LON).toInt
    val yIndex = Math.floor((LAT_NORTH - lat) / DELTA_LAT).toInt
    xIndex + (yIndex * NUMBER_OF_GRID_X)
  }

  /**
    * Maps the direct path between two locations specified by longitude and latitude to a list of
    * cells of a grid covering the area of NYC.
    * The grid cells are roughly 100 x 100 m and sequentially number from north-west
    * to south-east starting by zero.
    *
    * @param lon1 longitude of the first location
    * @param lat1 latitude of the first location
    * @param lon2 longitude of the second location
    * @param lat2 latitude of the second location
    * @return A list of cell ids
    */
  def mapToGridCellsOnWay(lon1: Float, lat1: Float, lon2: Float, lat2: Float): util.List[Integer] = {
    val x1 = Math.floor((Math.abs(LON_WEST) - Math.abs(lon1)) / DELTA_LON).toInt
    val y1 = Math.floor((LAT_NORTH - lat1) / DELTA_LAT).toInt
    val x2 = Math.floor((Math.abs(LON_WEST) - Math.abs(lon2)) / DELTA_LON).toInt
    val y2 = Math.floor((LAT_NORTH - lat2) / DELTA_LAT).toInt
    val square = (x1 <= x2) match {
      case true => (x1, y1, x2, y2)
      case _ => (x2, y2, x1, y1)
    }
    val slope = (square._4 - square._2) / ((square._3 - square._1) + 0.00000001)
    var curX = square._1
    var curY = square._2
    val cellIds: util.ArrayList[Integer] = new util.ArrayList[Integer](64)
    cellIds.add(curX + (curY * NUMBER_OF_GRID_X))
    while (curX < square._3 || curY != square._4) {
      slope match {
        case value if (value > 0) =>
          val y = (curX - square._1 + 0.5) * slope + square._2 - 0.5
          if (y > curY - 0.05 && y < curY + 0.05) {
            curX += 1
            curY += 1
          }
          else if (y < curY) curX += 1  else curY += 1
        case _ =>
          val y = (curX - square._1 + 0.5) * slope + square._2 + 0.5
          if (y > curY - 0.05 && y < curY + 0.05) {
            curX += 1
            curY -= 1
          }
          if (y > curY) curX += 1 else curY -= 1
      }
      cellIds.add(curX + (curY * NUMBER_OF_GRID_X))
    }
    cellIds
  }

  /**
    * Returns the longitude of the center of a grid cell.
    *
    * @param gridCellId The grid cell.
    * @return The longitude value of the cell's center.
    */
  def getGridCellCenterLon(gridCellId: Int): Float = {
    val xIndex = gridCellId % NUMBER_OF_GRID_X
    (Math.abs(LON_WEST) - (xIndex * DELTA_LON) - (DELTA_LON / 2)).toFloat * -1.0f
  }

  /**
    * Returns the latitude of the center of a grid cell.
    *
    * @param gridCellId The grid cell.
    * @return The latitude value of the cell's center.
    */
  def getGridCellCenterLat(gridCellId: Int): Float = {
    val xIndex = gridCellId % NUMBER_OF_GRID_X
    val yIndex = (gridCellId - xIndex) / NUMBER_OF_GRID_X
    (LAT_NORTH - (yIndex * DELTA_LAT) - (DELTA_LAT / 2)).toFloat
  }

  /**
    * Returns a random longitude within the NYC area.
    *
    * @param rand A random number generator.
    * @return A random longitude value within the NYC area.
    */
  def getRandomNYCLon(rand: Random): Float = (LON_EAST - (LON_WIDTH * rand.nextFloat)).toFloat

  /**
    * Returns a random latitude within the NYC area.
    *
    * @param rand A random number generator.
    * @return A random latitude value within the NYC area.
    */
  def getRandomNYCLat(rand: Random): Float = (LAT_SOUTH + (LAT_HEIGHT * rand.nextFloat)).toFloat

  /**
    * Returns the Euclidean distance between two locations specified as lon/lat pairs.
    *
    * @param lon1 Longitude of first location
    * @param lat1 Latitude of first location
    * @param lon2 Longitude of second location
    * @param lat2 Latitude of second location
    * @return The Euclidean distance between the specified locations.
    */
  def getEuclideanDistance(lon1: Float, lat1: Float, lon2: Float, lat2: Float) = {
    val x = lat1 - lat2
    val y = (lon1 - lon2) * Math.cos(lat2)
    DEG_LEN * Math.sqrt(x * x + y * y)
  }

  /**
    * Returns the angle in degrees between the vector from the start to the destination
    * and the x-axis on which the start is located.
    *
    * The angle describes in which direction the destination is located from the start, i.e.,
    * 0° -> East, 90° -> South, 180° -> West, 270° -> North
    *
    * @param startLon longitude of start location
    * @param startLat latitude of start location
    * @param destLon  longitude of destination
    * @param destLat  latitude of destination
    * @return The direction from start to destination location
    */
  def getDirectionAngle(startLon: Float, startLat: Float, destLon: Float, destLat: Float): Int = {
    val x = destLat - startLat
    val y = (destLon - startLon) * Math.cos(startLat)
    Math.toDegrees(Math.atan2(x, y)).toInt + 179
  }
}
