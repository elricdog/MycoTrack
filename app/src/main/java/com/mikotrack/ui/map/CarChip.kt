package com.mikotrack.ui.map

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikotrack.R
import com.mikotrack.core.Formatters

/** How far the car is from the user and in which direction. */
data class ParkingDistance(
    val distanceMeters: Double,
    val bearingDegrees: Double,
)

/** A compact chip with the distance and direction to the car, e.g. "🚗 1.2 km · NE". */
@Composable
fun CarChip(
    distance: ParkingDistance,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compass = stringArrayResource(R.array.format_compass_points)[Formatters.compassIndex(distance.bearingDegrees)]
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
    ) {
        Text(
            text = stringResource(R.string.field_car_chip, Formatters.distance(distance.distanceMeters), compass),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
