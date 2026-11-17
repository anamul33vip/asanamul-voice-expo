import { and, desc, eq } from "drizzle-orm";
import { drizzle } from "drizzle-orm/mysql2";
import { InsertNotice, InsertUser, appSettings, notices, users, voices } from "../drizzle/schema";
import { ENV } from "./_core/env";

let _db: ReturnType<typeof drizzle> | null = null;

export async function getDb() {
  if (!_db && process.env.DATABASE_URL) {
    try {
      _db = drizzle(process.env.DATABASE_URL);
    } catch {
      console.warn("[Database] Failed to connect");
      _db = null;
    }
  }
  return _db;
}

export async function upsertUser(user: InsertUser): Promise<void> {
  if (!user.openId) throw new Error("User openId is required for upsert");
  const db = await getDb();
  if (!db) return;

  const values: InsertUser = { openId: user.openId };
  const updateSet: Record<string, unknown> = {};
  const textFields = ["name", "email", "loginMethod"] as const;
  for (const field of textFields) {
    if (user[field] !== undefined) {
      values[field] = user[field] ?? null;
      updateSet[field] = user[field] ?? null;
    }
  }
  if (user.lastSignedIn !== undefined) {
    values.lastSignedIn = user.lastSignedIn;
    updateSet.lastSignedIn = user.lastSignedIn;
  } else {
    values.lastSignedIn = new Date();
    updateSet.lastSignedIn = new Date();
  }
  if (user.role !== undefined) {
    values.role = user.role;
    updateSet.role = user.role;
  } else if (user.openId === ENV.ownerOpenId) {
    values.role = "admin";
    updateSet.role = "admin";
  }
  await db.insert(users).values(values).onDuplicateKeyUpdate({ set: updateSet });
}

export async function getUserByOpenId(openId: string) {
  const db = await getDb();
  if (!db) return undefined;
  const result = await db.select().from(users).where(eq(users.openId, openId)).limit(1);
  return result[0];
}

export async function listPublishedVoices() {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(voices).where(eq(voices.isPublished, true)).orderBy(desc(voices.createdAt)).limit(100);
}

export async function listAllVoices() {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(voices).orderBy(desc(voices.createdAt)).limit(200);
}

export async function createVoice(input: {
  name: string;
  category: string;
  format: string;
  storageKey: string;
  coverKey?: string;
  durationMs?: number;
  isPublished?: boolean;
}) {
  const db = await getDb();
  if (!db) throw new Error("Database is not configured");
  await db.insert(voices).values(input);
  return { success: true } as const;
}

export async function updateVoice(id: number, input: Partial<{
  name: string;
  category: string;
  format: string;
  storageKey: string;
  coverKey: string | null;
  durationMs: number | null;
  isPublished: boolean;
}>) {
  const db = await getDb();
  if (!db) throw new Error("Database is not configured");
  await db.update(voices).set(input).where(eq(voices.id, id));
  return { success: true } as const;
}

export async function deleteVoice(id: number) {
  const db = await getDb();
  if (!db) throw new Error("Database is not configured");
  await db.delete(voices).where(eq(voices.id, id));
  return { success: true } as const;
}

export async function getSettings() {
  const db = await getDb();
  const defaults = {
    telegramUrl: "https://t.me/crackffpanel",
    youtubeUrl: "https://www.youtube.com/@Anamul_Islamic_Echo",
    targetApp: "Free Fire",
    minAndroid: "12",
    maxAndroid: "15+",
  };
  if (!db) return defaults;
  const rows = await db.select().from(appSettings);
  return rows.reduce<Record<string, string>>((result, row) => {
    result[row.key] = row.value;
    return result;
  }, defaults);
}

export async function setSetting(key: string, value: string) {
  const db = await getDb();
  if (!db) throw new Error("Database is not configured");
  await db.insert(appSettings).values({ key, value }).onDuplicateKeyUpdate({ set: { value } });
  return { success: true } as const;
}

export async function listPublishedNotices() {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(notices).where(eq(notices.isPublished, true)).orderBy(desc(notices.createdAt)).limit(20);
}

export async function listAllNotices() {
  const db = await getDb();
  if (!db) return [];
  return db.select().from(notices).orderBy(desc(notices.createdAt)).limit(100);
}

export async function createNotice(input: InsertNotice) {
  const db = await getDb();
  if (!db) throw new Error("Database is not configured");
  await db.insert(notices).values(input);
  return { success: true } as const;
}

export async function deleteNotice(id: number) {
  const db = await getDb();
  if (!db) throw new Error("Database is not configured");
  await db.delete(notices).where(eq(notices.id, id));
  return { success: true } as const;
}
