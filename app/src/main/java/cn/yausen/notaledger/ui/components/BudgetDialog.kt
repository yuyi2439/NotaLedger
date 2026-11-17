package cn.yausen.notaledger.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.yausen.notaledger.util.Money

/**
 * Sets (or clears) the monthly spending budget. An empty value removes the
 * budget entirely, which is how the user turns the reminder off.
 */
@Composable
fun BudgetDialog(
    current: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit,
) {
    var text by remember {
        mutableStateOf(current?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("月度预算") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "留空表示不设置预算。预算只统计支出，不含收入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = Money.sanitizeInput(it)
                        error = null
                    },
                    label = { Text("每月支出上限（元）") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) {
                    onConfirm(null)
                    return@TextButton
                }
                val value = Money.parse(text)
                val cents = value?.let { runCatching { cn.yausen.notaledger.data.Money.toCents(it) }.getOrNull() }
                if (cents == null || cents <= 0L) {
                    error = "请输入大于 0 且未超出支持范围的金额"
                } else {
                    onConfirm(value)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
