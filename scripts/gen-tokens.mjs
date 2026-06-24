#!/usr/bin/env node
// Generates Kotlin, CSS, and TypeScript artifacts from design-system-docs/tokens.json.
// Run: node scripts/gen-tokens.mjs

import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(__dirname, "..");
const TOKENS_PATH = path.join(REPO, "design-system-docs/tokens.json");
const KOTLIN_OUT = path.join(REPO, "app/src/main/kotlin/com/nendo/argosy/ui/theme/generated");

const HEADER_KT = `// AUTO-GENERATED. DO NOT EDIT.
// Source: design-system-docs/tokens.json
// Run: node scripts/gen-tokens.mjs
`;

// ---------- color helpers ----------

function parseHex(hex) {
  if (!/^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$/.test(hex)) {
    throw new Error(`bad hex: ${hex}`);
  }
  return {
    r: parseInt(hex.slice(1, 3), 16),
    g: parseInt(hex.slice(3, 5), 16),
    b: parseInt(hex.slice(5, 7), 16),
    a: hex.length === 9 ? parseInt(hex.slice(7, 9), 16) / 255 : 1
  };
}

function alphaByte(alpha) {
  return Math.round(alpha * 255).toString(16).padStart(2, "0").toUpperCase();
}

function colorToKotlin(value) {
  if (typeof value === "string") {
    const { r, g, b, a } = parseHex(value);
    const ab = alphaByte(a);
    return `Color(0x${ab}${value.slice(1, 7).toUpperCase()})`;
  }
  if (value && typeof value === "object" && "color" in value && "alpha" in value) {
    const ab = alphaByte(value.alpha);
    return `Color(0x${ab}${value.color.slice(1, 7).toUpperCase()})`;
  }
  throw new Error(`unexpected color: ${JSON.stringify(value)}`);
}

function colorToCss(value) {
  if (typeof value === "string") {
    return value.toUpperCase();
  }
  if (value && typeof value === "object" && "color" in value && "alpha" in value) {
    const { r, g, b } = parseHex(value.color);
    return `rgba(${r}, ${g}, ${b}, ${value.alpha})`;
  }
  throw new Error(`unexpected color: ${JSON.stringify(value)}`);
}

// ---------- name helpers ----------

const kebab = (s) => s.replace(/([a-z0-9])([A-Z])/g, "$1-$2").replace(/_/g, "-").toLowerCase();
const pascal = (s) => s.charAt(0).toUpperCase() + s.slice(1);

// ---------- Kotlin emitters ----------

function emitColorTokens(color) {
  const out = [HEADER_KT, `@file:Suppress("unused")`, ``, `package com.nendo.argosy.ui.theme.generated`, ``, `import androidx.compose.ui.graphics.Color`, ``];

  out.push(`object ColorTokens {`);

  // Scheme
  out.push(`    object Scheme {`);
  for (const mode of ["dark", "light"]) {
    out.push(`        object ${pascal(mode)} {`);
    for (const [slot, value] of Object.entries(color.scheme[mode])) {
      out.push(`            val ${slot} = ${colorToKotlin(value)}`);
    }
    out.push(`        }`);
  }
  out.push(`        object DebugOverrides {`);
  for (const mode of ["dark", "light"]) {
    out.push(`            object ${pascal(mode)} {`);
    for (const [slot, value] of Object.entries(color.scheme.debugOverrides[mode])) {
      out.push(`                val ${slot} = ${colorToKotlin(value)}`);
    }
    out.push(`            }`);
  }
  out.push(`        }`);
  out.push(`    }`);
  out.push(``);

  // Semantic
  out.push(`    object Semantic {`);
  for (const mode of ["dark", "light"]) {
    out.push(`        object ${pascal(mode)} {`);
    for (const [slot, value] of Object.entries(color.semantic[mode])) {
      out.push(`            val ${slot} = ${colorToKotlin(value)}`);
    }
    out.push(`        }`);
  }
  out.push(`    }`);
  out.push(``);

  // Domain
  out.push(`    object Domain {`);
  for (const [key, value] of Object.entries(color.domain)) {
    if (isColorValue(value)) {
      out.push(`        val ${key} = ${colorToKotlin(value)}`);
    } else if (isModePair(value)) {
      out.push(`        object ${pascal(key)} {`);
      out.push(`            val dark = ${colorToKotlin(value.dark)}`);
      out.push(`            val light = ${colorToKotlin(value.light)}`);
      out.push(`        }`);
    } else {
      // nested group of colorValue or modePair
      out.push(`        object ${pascal(key)} {`);
      for (const [subKey, subValue] of Object.entries(value)) {
        if (isColorValue(subValue)) {
          out.push(`            val ${subKey} = ${colorToKotlin(subValue)}`);
        } else if (isModePair(subValue)) {
          out.push(`            object ${pascal(subKey)} {`);
          out.push(`                val dark = ${colorToKotlin(subValue.dark)}`);
          out.push(`                val light = ${colorToKotlin(subValue.light)}`);
          out.push(`            }`);
        } else {
          throw new Error(`unexpected domain shape at ${key}.${subKey}`);
        }
      }
      out.push(`        }`);
    }
  }
  out.push(`    }`);
  out.push(``);

  // Accent presets
  out.push(`    val accentPresets: List<AccentPreset> = listOf(`);
  for (const preset of color.accentPresets) {
    out.push(`        AccentPreset(dark = ${colorToKotlin(preset.dark)}, light = ${colorToKotlin(preset.light)}),`);
  }
  out.push(`    )`);

  out.push(`}`);
  out.push(``);
  out.push(`data class AccentPreset(val dark: Color, val light: Color)`);
  out.push(``);

  return out.join("\n");
}

function isColorValue(v) {
  if (typeof v === "string") return /^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$/.test(v);
  if (v && typeof v === "object" && "color" in v && "alpha" in v) return true;
  return false;
}

function isModePair(v) {
  return v && typeof v === "object" && "dark" in v && "light" in v
    && isColorValue(v.dark) && isColorValue(v.light);
}

function emitDimensionTokens(dimension) {
  const out = [HEADER_KT, `@file:Suppress("unused")`, ``, `package com.nendo.argosy.ui.theme.generated`, ``];

  out.push(`object DimensionTokens {`);

  out.push(`    object Spacing {`);
  for (const [name, { base, floor }] of Object.entries(dimension.spacing)) {
    out.push(`        object ${pascal(name)} { const val base = ${base}; const val floor = ${floor} }`);
  }
  out.push(`    }`);
  out.push(``);

  for (const group of ["radius", "border", "icon", "dot", "layout", "elevation"]) {
    out.push(`    object ${pascal(group)} {`);
    for (const [name, value] of Object.entries(dimension[group])) {
      out.push(`        const val ${name} = ${value}`);
    }
    out.push(`    }`);
    out.push(``);
  }

  out.push(`    object UiScale {`);
  out.push(`        const val min = ${dimension.uiScale.min}`);
  out.push(`        const val max = ${dimension.uiScale.max}`);
  out.push(`        const val default = ${dimension.uiScale.default}`);
  out.push(`        const val step = ${dimension.uiScale.step}`);
  out.push(`    }`);

  out.push(`}`);
  out.push(``);

  return out.join("\n");
}

function emitTypographyTokens(typography) {
  const out = [
    HEADER_KT,
    `@file:Suppress("unused")`,
    ``,
    `package com.nendo.argosy.ui.theme.generated`,
    ``,
    `import androidx.compose.material3.Typography`,
    `import androidx.compose.ui.text.TextStyle`,
    `import androidx.compose.ui.text.font.FontFamily`,
    `import androidx.compose.ui.text.font.FontWeight`,
    `import androidx.compose.ui.unit.sp`,
    ``,
    `object TypographyTokens {`,
  ];

  for (const [name, style] of Object.entries(typography)) {
    const lines = [];
    lines.push(`        fontFamily = FontFamily.${style.fontFamily || "SansSerif"}`);
    lines.push(`        fontWeight = FontWeight(${style.fontWeight})`);
    lines.push(`        fontSize = ${style.fontSize}.sp`);
    lines.push(`        lineHeight = ${style.lineHeight}.sp`);
    if (style.letterSpacing !== undefined) {
      const ls = style.letterSpacing < 0
        ? `(${style.letterSpacing}).sp`
        : `${style.letterSpacing}.sp`;
      lines.push(`        letterSpacing = ${ls}`);
    }
    out.push(`    val ${name}: TextStyle = TextStyle(`);
    out.push(lines.map(l => l).join(",\n"));
    out.push(`    )`);
    out.push(``);
  }

  out.push(`    val Material3: Typography = Typography(`);
  const styleNames = Object.keys(typography);
  for (let i = 0; i < styleNames.length; i++) {
    const sep = i < styleNames.length - 1 ? "," : "";
    out.push(`        ${styleNames[i]} = ${styleNames[i]}${sep}`);
  }
  out.push(`    )`);
  out.push(`}`);
  out.push(``);

  return out.join("\n");
}

function emitMotionTokens(motion) {
  const out = [
    HEADER_KT,
    `@file:Suppress("unused")`,
    ``,
    `package com.nendo.argosy.ui.theme.generated`,
    ``,
    `import androidx.compose.animation.core.AnimationSpec`,
    `import androidx.compose.animation.core.spring`,
    `import androidx.compose.animation.core.tween`,
    `import androidx.compose.ui.graphics.Color`,
    `import androidx.compose.ui.unit.Dp`,
    ``,
    `object MotionTokens {`,
  ];

  out.push(`    object Spring {`);
  for (const [name, spec] of Object.entries(motion.spring)) {
    const N = pascal(name);
    out.push(`        const val ${name}DampingRatio = ${spec.dampingRatio}f`);
    out.push(`        const val ${name}Stiffness = ${spec.stiffness}f`);
    out.push(`        val ${name}: AnimationSpec<Float> = spring(dampingRatio = ${name}DampingRatio, stiffness = ${name}Stiffness)`);
    out.push(`        val ${name}Dp: AnimationSpec<Dp> = spring(dampingRatio = ${name}DampingRatio, stiffness = ${name}Stiffness)`);
    out.push(`        val ${name}Color: AnimationSpec<Color> = spring(dampingRatio = ${name}DampingRatio, stiffness = ${name}Stiffness)`);
  }
  out.push(`    }`);
  out.push(``);

  out.push(`    object Tween {`);
  for (const [name, ms] of Object.entries(motion.tween)) {
    out.push(`        const val ${name}Ms = ${ms}`);
    out.push(`        val ${name}: AnimationSpec<Float> = tween(durationMillis = ${name}Ms)`);
  }
  out.push(`    }`);
  out.push(``);

  out.push(`}`);
  out.push(``);

  return out.join("\n");
}

function emitInputTokens(input) {
  const out = [
    HEADER_KT,
    `@file:Suppress("unused")`,
    ``,
    `package com.nendo.argosy.ui.theme.generated`,
    ``,
    `object InputTokens {`,
  ];

  out.push(`    object Debounce {`);
  for (const [name, value] of Object.entries(input.debounce)) {
    out.push(`        const val ${name} = ${value}L`);
  }
  out.push(`    }`);
  out.push(``);
  out.push(`    const val scrollPaddingPercent = ${input.scrollPaddingPercent}f`);
  out.push(`}`);
  out.push(``);

  return out.join("\n");
}

function emitComponentDefaults(components, enums) {
  const out = [
    HEADER_KT,
    `@file:Suppress("unused")`,
    ``,
    `package com.nendo.argosy.ui.theme.generated`,
    ``,
    `import com.nendo.argosy.data.cache.GradientPreset`,
    `import com.nendo.argosy.data.preferences.BoxArtBorderStyle`,
    `import com.nendo.argosy.data.preferences.BoxArtBorderThickness`,
    `import com.nendo.argosy.data.preferences.BoxArtCornerRadius`,
    `import com.nendo.argosy.data.preferences.BoxArtGlowStrength`,
    `import com.nendo.argosy.data.preferences.BoxArtInnerEffect`,
    `import com.nendo.argosy.data.preferences.BoxArtInnerEffectThickness`,
    `import com.nendo.argosy.data.preferences.BoxArtOuterEffect`,
    `import com.nendo.argosy.data.preferences.BoxArtOuterEffectThickness`,
    `import com.nendo.argosy.data.preferences.BoxArtShape`,
    `import com.nendo.argosy.data.preferences.DefaultView`,
    `import com.nendo.argosy.data.preferences.DisplayRoleOverride`,
    `import com.nendo.argosy.data.preferences.DualScreenInputFocus`,
    `import com.nendo.argosy.data.preferences.GlassBorderTint`,
    `import com.nendo.argosy.data.preferences.GlowColorMode`,
    `import com.nendo.argosy.data.preferences.GridDensity`,
    `import com.nendo.argosy.data.preferences.SystemIconPadding`,
    `import com.nendo.argosy.data.preferences.SystemIconPosition`,
    `import com.nendo.argosy.data.preferences.ThemeMode`,
    ``,
    `object ComponentDefaults {`,
  ];

  // Map of token-side enum names → Kotlin import names. They happen to match,
  // but this mapping is the contract.
  const enumNameMap = {
    BoxArtShape: "BoxArtShape",
    BoxArtCornerRadius: "BoxArtCornerRadius",
    BoxArtBorderThickness: "BoxArtBorderThickness",
    BoxArtBorderStyle: "BoxArtBorderStyle",
    GlassBorderTint: "GlassBorderTint",
    BoxArtGlowStrength: "BoxArtGlowStrength",
    BoxArtOuterEffect: "BoxArtOuterEffect",
    BoxArtOuterEffectThickness: "BoxArtOuterEffectThickness",
    BoxArtInnerEffect: "BoxArtInnerEffect",
    BoxArtInnerEffectThickness: "BoxArtInnerEffectThickness",
    GlowColorMode: "GlowColorMode",
    SystemIconPosition: "SystemIconPosition",
    SystemIconPadding: "SystemIconPadding",
    GridDensity: "GridDensity",
    ThemeMode: "ThemeMode",
    DefaultView: "DefaultView",
    DisplayRoleOverride: "DisplayRoleOverride",
    DualScreenInputFocus: "DualScreenInputFocus",
    GradientPreset: "GradientPreset",
  };

  // Field-name → token-enum mapping. Order maps each component field to the
  // enum it references. If a field name appears in this map, its value is
  // interpreted as an enum member name and rendered as `Enum.MEMBER`.
  const fieldEnumMap = {
    // boxArt
    shape: "BoxArtShape",
    cornerRadius: "BoxArtCornerRadius",
    borderThickness: "BoxArtBorderThickness",
    borderStyle: "BoxArtBorderStyle",
    glassBorderTint: "GlassBorderTint",
    glowStrength: "BoxArtGlowStrength",
    outerEffect: "BoxArtOuterEffect",
    outerEffectThickness: "BoxArtOuterEffectThickness",
    innerEffect: "BoxArtInnerEffect",
    innerEffectThickness: "BoxArtInnerEffectThickness",
    glowColorMode: "GlowColorMode",
    systemIconPosition: "SystemIconPosition",
    systemIconPadding: "SystemIconPadding",
    // launcher
    themeMode: "ThemeMode",
    defaultView: "DefaultView",
    gridDensity: "GridDensity",
    // background
    gradientPreset: "GradientPreset",
    // dualScreen
    displayRoleOverride: "DisplayRoleOverride",
    dualScreenInputFocus: "DualScreenInputFocus",
  };

  function isFloatField(field) {
    const f = field.toLowerCase();
    return f.includes("alpha") || f.includes("scale") || f.includes("saturation")
        || f.includes("ratio") || f.includes("percent");
  }

  function renderValue(field, value) {
    if (field in fieldEnumMap) {
      const enumName = fieldEnumMap[field];
      if (!enums[enumName]) throw new Error(`unknown enum: ${enumName}`);
      // Validate the value is a real enum member
      const values = Array.isArray(enums[enumName].values)
        ? enums[enumName].values
        : Object.keys(enums[enumName].values);
      if (!values.includes(value)) {
        throw new Error(`enum ${enumName} has no member ${value}`);
      }
      return `${enumNameMap[enumName]}.${value}`;
    }
    if (typeof value === "boolean") return value ? "true" : "false";
    if (typeof value === "number") {
      // Force `f` suffix for fields known to be Float-typed in Kotlin so whole-number
      // values (1, 0) don't get inferred as Int and break the consumer's signature.
      if (isFloatField(field) || !Number.isInteger(value)) {
        return `${value}f`;
      }
      return `${value}`;
    }
    if (typeof value === "string") return `"${value}"`;
    throw new Error(`unsupported component default: ${field} = ${JSON.stringify(value)}`);
  }

  function valKind(field, value) {
    // const val only works for primitive constants. Enums and other types need `val`.
    if (field in fieldEnumMap) return "val";
    if (typeof value === "string") return "const val";
    if (typeof value === "boolean") return "const val";
    if (typeof value === "number") return "const val";
    return "val";
  }

  for (const [compName, fields] of Object.entries(components)) {
    out.push(`    object ${pascal(compName)} {`);
    for (const [field, value] of Object.entries(fields)) {
      out.push(`        ${valKind(field, value)} ${field} = ${renderValue(field, value)}`);
    }
    out.push(`    }`);
    out.push(``);
  }

  out.push(`}`);
  out.push(``);

  return out.join("\n");
}

// ---------- main ----------

async function main() {
  const raw = await fs.readFile(TOKENS_PATH, "utf8");
  const tokens = JSON.parse(raw);

  await fs.mkdir(KOTLIN_OUT, { recursive: true });

  const writes = [
    [path.join(KOTLIN_OUT, "ColorTokens.kt"),       emitColorTokens(tokens.color)],
    [path.join(KOTLIN_OUT, "DimensionTokens.kt"),   emitDimensionTokens(tokens.dimension)],
    [path.join(KOTLIN_OUT, "TypographyTokens.kt"),  emitTypographyTokens(tokens.typography)],
    [path.join(KOTLIN_OUT, "MotionTokens.kt"),      emitMotionTokens(tokens.motion)],
    [path.join(KOTLIN_OUT, "InputTokens.kt"),       emitInputTokens(tokens.input)],
    [path.join(KOTLIN_OUT, "ComponentDefaults.kt"), emitComponentDefaults(tokens.components, tokens.enums)],
  ];

  for (const [outPath, content] of writes) {
    await fs.writeFile(outPath, content, "utf8");
    const rel = path.relative(REPO, outPath);
    console.log(`wrote ${rel} (${content.length} bytes)`);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
