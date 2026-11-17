import { z } from "zod";
import { getSessionCookieName, getSessionCookieOptions } from "./_core/cookies";
import { systemRouter } from "./_core/systemRouter";
import { adminProcedure, publicProcedure, router } from "./_core/trpc";
import {
  createNotice,
  createVoice,
  deleteNotice,
  deleteVoice,
  getSettings,
  listAllNotices,
  listAllVoices,
  listPublishedNotices,
  listPublishedVoices,
  setSetting,
  updateVoice,
} from "./db";

const voiceInput = z.object({
  name: z.string().min(1).max(160),
  category: z.string().min(1).max(80),
  format: z.enum(["mp3", "wav", "m4a", "ogg"]),
  storageKey: z.string().min(1).max(512),
  coverKey: z.string().max(512).optional(),
  durationMs: z.number().int().positive().optional(),
  isPublished: z.boolean().default(true),
});

export const appRouter = router({
  system: systemRouter,
  auth: router({
    me: publicProcedure.query((opts) => opts.ctx.user),
    logout: publicProcedure.mutation(({ ctx }) => {
      const cookieOptions = getSessionCookieOptions(ctx.req);
      ctx.res.clearCookie(getSessionCookieName(ctx.req), { ...cookieOptions, maxAge: -1 });
      return { success: true } as const;
    }),
  }),
  catalog: publicProcedure.query(async () => ({
    voices: await listPublishedVoices(),
    notices: await listPublishedNotices(),
    settings: await getSettings(),
  })),
  admin: router({
    voices: adminProcedure.query(() => listAllVoices()),
    notices: adminProcedure.query(() => listAllNotices()),
    createVoice: adminProcedure.input(voiceInput).mutation(({ input }) => createVoice(input)),
    updateVoice: adminProcedure
      .input(z.object({ id: z.number().int().positive(), data: voiceInput.partial() }))
      .mutation(({ input }) => updateVoice(input.id, input.data)),
    deleteVoice: adminProcedure
      .input(z.object({ id: z.number().int().positive() }))
      .mutation(({ input }) => deleteVoice(input.id)),
    setSetting: adminProcedure
      .input(z.object({ key: z.string().min(1).max(120), value: z.string().max(2000) }))
      .mutation(({ input }) => setSetting(input.key, input.value)),
    createNotice: adminProcedure
      .input(z.object({ title: z.string().min(1).max(180), body: z.string().min(1), version: z.string().max(32).optional(), isPublished: z.boolean().default(true) }))
      .mutation(({ input }) => createNotice(input)),
    deleteNotice: adminProcedure
      .input(z.object({ id: z.number().int().positive() }))
      .mutation(({ input }) => deleteNotice(input.id)),
  }),
});

export type AppRouter = typeof appRouter;
